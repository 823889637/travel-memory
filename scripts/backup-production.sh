#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
cd "$project_root"

if docker compose version >/dev/null 2>&1; then
  compose=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose=(docker-compose)
else
  echo "Docker Compose is required." >&2
  exit 1
fi

timestamp="$(date +%Y-%m-%d_%H-%M-%S)"
backup_dir="$project_root/backups/$timestamp"
mkdir -p "$backup_dir"

db_dump="$backup_dir/mysql.sql"
db_temp="$db_dump.tmp"
echo "Backing up MySQL to $db_dump"
if ! "${compose[@]}" exec -T mysql sh -c 'mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' > "$db_temp"; then
  rm -f "$db_temp"
  echo "MySQL export failed; no completed database backup was created." >&2
  exit 1
fi
mv "$db_temp" "$db_dump"

backend_container="$("${compose[@]}" ps -q backend)"
if [[ -z "$backend_container" ]]; then
  echo "Backend container is not running; cannot determine the mounted uploads directory." >&2
  exit 1
fi

uploads_dir="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/uploads"}}{{.Source}}{{end}}{{end}}' "$backend_container")"
if [[ -z "$uploads_dir" || ! -d "$uploads_dir" ]]; then
  echo "Uploads directory is unavailable at the backend container mount: ${uploads_dir:-not mounted}." >&2
  exit 1
fi

uploads_archive="$backup_dir/uploads.tar.gz"
echo "Backing up uploads from $uploads_dir to $uploads_archive"
tar -C "$(dirname "$uploads_dir")" -czf "$uploads_archive" "$(basename "$uploads_dir")"

echo "Backup completed:"
du -h "$db_dump" "$uploads_archive"
