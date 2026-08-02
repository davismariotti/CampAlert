# Build context is the repo's `db/` directory (see release.yml), so paths below
# are relative to `db/`: `schema.sql`/`atlas.hcl` at the root, this Dockerfile
# and main.go under `migrate/`.

FROM golang:1.22-bookworm AS build
WORKDIR /src

COPY migrate/go.mod migrate/go.sum ./
RUN go mod download

COPY migrate/main.go ./

ARG TARGETOS
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} go build -o /migrate .

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

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 10001 migrate && useradd -u 10001 -g migrate -m migrate

COPY --from=build --chown=migrate:migrate /migrate /app/migrate
COPY --from=build --chown=migrate:migrate /opt/embedded-postgres/binaries /opt/embedded-postgres/binaries
RUN mkdir -p /opt/embedded-postgres/runtime /opt/embedded-postgres/data \
    && chown -R migrate:migrate /opt/embedded-postgres

# NOTE: assumes the atlas binary lives at /atlas in arigaio/atlas — unverified,
# needs confirming against a real pull before this Dockerfile is trusted.
COPY --from=arigaio/atlas:latest /atlas /app/atlas

COPY --chown=migrate:migrate schema.sql /app/schema.sql
COPY --chown=migrate:migrate atlas.hcl /app/atlas.hcl

USER migrate
ENTRYPOINT ["/app/migrate"]
