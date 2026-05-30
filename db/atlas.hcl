env "local" {
  src = "file://schema.sql"
  url = getenv("CAMPALERT_DB_URL")
}
