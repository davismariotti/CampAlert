lint {
  destructive {
    error = true
  }
}

env "local" {
  src = "file://schema.sql"
  url = getenv("CAMPALERT_DB_URL")
}
