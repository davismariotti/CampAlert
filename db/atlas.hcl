env "local" {
  src = "file://schema.sql"
  url = getenv("CAMPFINDER_DB_URL")
}
