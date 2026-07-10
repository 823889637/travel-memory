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
uploads_archive="$backup_dir/uploads.tar.gz"
checksums="$backup_dir/SHA256SUMS"
incomplete_marker="$backup_dir/INCOMPLETE"
stage="backup initialization"

printf 'Backup incomplete. Do not use this directory for recovery.\n' > "$incomplete_marker"

cleanup() {
  status=$?
  rm -f "$db_temp"
  if [[ "$status" -ne 0 ]]; then
    printf 'Backup incomplete: %s failed. The INCOMPLETE marker remains in %s.\n' "$stage" "$backup_dir" > "$incomplete_marker"
    echo "Backup failed during $stage. Existing completed files were retained, but this backup set is incomplete." >&2
  fi
  exit "$status"
}
trap cleanup EXIT

stage="MySQL export"
echo "Backing up MySQL to $db_dump"
if ! "${compose[@]}" exec -T mysql sh -c '
  set -eu
  umask 077
  option_file="$(mktemp /tmp/travel-memory-mysqldump.XXXXXX)"
  cleanup_option_file() { rm -f "$option_file"; }
  trap cleanup_option_file EXIT HUP INT TERM
  printf "[client]\nuser=%s\npassword=%s\n" "$MYSQL_USER" "$MYSQL_PASSWORD" > "$option_file"
  chmod 600 "$option_file"
  mysqldump --defaults-extra-file="$option_file" --single-transaction --quick --skip-lock-tables --no-tablespaces --triggers "$MYSQL_DATABASE"
' > "$db_temp"; then
  exit 1
fi

if [[ ! -s "$db_temp" ]]; then
  echo "MySQL export produced an empty dump." >&2
  exit 1
fi
mv "$db_temp" "$db_dump"

stage="backend uploads mount lookup"
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
uploads_dir="$(cd -P "$uploads_dir" && pwd)"

stage="uploads archive"
echo "Backing up uploads from $uploads_dir to $uploads_archive"
tar -C "$(dirname "$uploads_dir")" -czf "$uploads_archive" "$(basename "$uploads_dir")"
if [[ ! -s "$uploads_archive" ]]; then
  echo "Uploads archive is empty." >&2
  exit 1
fi

stage="checksum generation"
(
  cd "$backup_dir"
  sha256sum mysql.sql uploads.tar.gz > "$(basename "$checksums")"
  sha256sum -c "$(basename "$checksums")"
)

rm -f "$incomplete_marker"
trap - EXIT

echo "Backup completed:"
du -h "$db_dump" "$uploads_archive"
echo "Checksums verified: $checksums"
