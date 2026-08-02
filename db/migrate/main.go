package main

import (
	"bytes"
	"errors"
	"flag"
	"fmt"
	"log"
	"os"
	"os/exec"
	"time"

	embeddedpostgres "github.com/fergusstrange/embedded-postgres"
)

const (
	binariesPath = "/opt/embedded-postgres/binaries"
	runtimePath  = "/opt/embedded-postgres/runtime"
	dataPath     = "/opt/embedded-postgres/data"
	devPort      = uint32(25432)
	devUser      = "atlas"
	devPassword  = "atlas"
	devDatabase  = "atlas_dev"

	atlasBinary = "/app/atlas"
	schemaFile  = "file:///app/schema.sql"
	atlasConfig = "file:///app/atlas.hcl"
)

func main() {
	warmCache := flag.Bool("warm-cache", false, "download and extract the embedded Postgres binaries, then exit (used at image build time only)")
	flag.Parse()

	// Mirrors db/scripts/atlas.sh's own `diff|apply` convention: the mode is
	// picked by how the container is invoked (`docker run ... IMAGE diff`),
	// defaulting to the read-only `diff` the same way atlas.sh does.
	mode := "diff"
	if flag.NArg() > 0 {
		mode = flag.Arg(0)
	}

	os.Exit(run(*warmCache, mode))
}

func run(warmCache bool, mode string) int {
	// Postgres's own initdb/startup/shutdown chatter is noise on the common
	// path (a deploy where nothing goes wrong) — buffer it instead of piping
	// straight to stdout, and only dump it if Start() actually fails, so the
	// diagnostic detail is still there exactly when it's needed.
	var postgresLog bytes.Buffer

	config := embeddedpostgres.DefaultConfig().
		Version(embeddedpostgres.V16).
		Username(devUser).
		Password(devPassword).
		Database(devDatabase).
		Port(devPort).
		BinariesPath(binariesPath).
		RuntimePath(runtimePath).
		DataPath(dataPath).
		StartTimeout(45 * time.Second).
		Logger(&postgresLog)

	postgres := embeddedpostgres.NewDatabase(config)

	// Start() downloads/extracts the binaries into BinariesPath if they
	// aren't already there, so this same call is reused both to warm the
	// cache at image-build time and to run the real dev-db at deploy time.
	if err := postgres.Start(); err != nil {
		log.Printf("failed to start embedded postgres: %v", err)
		os.Stderr.Write(postgresLog.Bytes())
		return 1
	}
	defer func() {
		if err := postgres.Stop(); err != nil {
			log.Printf("warning: failed to stop embedded postgres cleanly: %v", err)
		}
	}()

	if warmCache {
		// Image-build-time only: nothing left to do once Start() has
		// populated BinariesPath. The Dockerfile COPYs that directory into
		// the runtime stage so the real deploy-time run never re-downloads.
		return 0
	}

	targetURL := os.Getenv("MIGRATE_TARGET_DB_URL")
	if targetURL == "" {
		log.Println("MIGRATE_TARGET_DB_URL is required")
		return 1
	}

	devURL := fmt.Sprintf("postgres://%s:%s@localhost:%d/%s?sslmode=disable", devUser, devPassword, devPort, devDatabase)

	var args []string
	switch mode {
	case "diff":
		// Read-only: reports what `apply` would do without touching the target.
		args = []string{
			"schema", "diff",
			"--from", targetURL,
			"--to", schemaFile,
			"--dev-url", devURL,
		}
	case "apply":
		args = []string{
			"schema", "apply",
			"--url", targetURL,
			"--to", schemaFile,
			"--dev-url", devURL,
			"--config", atlasConfig,
			"--auto-approve",
		}
	default:
		log.Printf("unknown mode %q — usage: migrate [diff|apply]", mode)
		return 1
	}

	cmd := exec.Command(atlasBinary, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			log.Printf("atlas exited with an error: %v", err)
			return exitErr.ExitCode()
		}
		log.Printf("failed to run atlas: %v", err)
		return 1
	}

	return 0
}
