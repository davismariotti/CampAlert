# Build context is the repo's `db/` directory (see release.yml), so paths below
# are relative to `db/`: `schema.sql`/`atlas.hcl` at the root, this Dockerfile
# and main.go under `migrate/`.

# Pinned to the build host's own platform (not the target platform being built
# for) so cross-compiling for arm64 on an amd64 CI runner never invokes QEMU —
# Go's toolchain cross-compiles natively via GOOS/GOARCH. This is the slowest
# part of the whole build under emulation (~180s vs ~15s native), and nothing
# in this stage needs to actually execute target-platform code, only compile
# it, so there's no reason to pay the emulation cost here at all.
FROM --platform=$BUILDPLATFORM golang:1.22-bookworm AS compile
WORKDIR /src

COPY migrate/go.mod migrate/go.sum ./
RUN go mod download

COPY migrate/main.go ./

ARG TARGETOS
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} go build -o /migrate .

# This stage runs under the real target platform (emulated via QEMU when
# cross-building) because warm-cache has to actually execute the compiled
# target-arch /migrate binary, not just produce it — unlike compilation,
# execution can't be cross-done. It's a much smaller cost than compilation
# was: mostly network I/O (the Postgres binary download), which emulation
# overhead barely touches, unlike CPU-bound work like `go build`.
FROM golang:1.22-bookworm AS build
COPY --from=compile /migrate /migrate

# initdb refuses to run as root, so the warm-cache step (and later, the real
# deploy-time run) both need a non-root user.
RUN groupadd -g 10001 migrate && useradd -u 10001 -g migrate -m migrate
RUN mkdir -p /opt/embedded-postgres/binaries /opt/embedded-postgres/runtime /opt/embedded-postgres/data \
    && chown -R migrate:migrate /opt/embedded-postgres
USER migrate

# Pre-cache the embedded Postgres binaries at build time (network access is
# fine here — this runs in CI) so the deploy-time container never needs to
# reach the binary distribution host. See db/migrate: task 2.2 / design.md.
RUN /migrate -warm-cache

FROM debian:bookworm-slim

# golang:1.22-bookworm (used by the compile stage) already carries
# ca-certificates as part of its base Debian image — copying it directly
# avoids an `apt-get update && install` here, which was otherwise the other
# major emulated-arm64 cost (~130s), for a package this image already has.
COPY --from=compile /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt

RUN groupadd -g 10001 migrate && useradd -u 10001 -g migrate -m migrate

COPY --from=build --chown=migrate:migrate /migrate /app/migrate
COPY --from=build --chown=migrate:migrate /opt/embedded-postgres/binaries /opt/embedded-postgres/binaries
RUN mkdir -p /opt/embedded-postgres/runtime /opt/embedded-postgres/data \
    && chown -R migrate:migrate /opt/embedded-postgres

COPY --from=arigaio/atlas:latest /atlas /app/atlas

COPY --chown=migrate:migrate schema.sql /app/schema.sql
COPY --chown=migrate:migrate atlas.hcl /app/atlas.hcl

USER migrate
ENTRYPOINT ["/app/migrate"]
