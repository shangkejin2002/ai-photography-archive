# Lightsail runtime

This directory contains the checked-in, non-secret runtime configuration for the
single-instance Lightsail deployment. Runtime secrets live only in the server's
`/opt/kejin-photo-archive/.env` file.

- `docker-compose.yml` runs the Spring Boot backend on loopback port 8080.
- `nginx.conf` serves the built frontend and proxies `/api/` to the backend.
- Existing H2 data and local photo storage are bind-mounted from `data/` so the
  first production release preserves the current application state exactly.

