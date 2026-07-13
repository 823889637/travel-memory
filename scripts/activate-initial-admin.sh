#!/usr/bin/env bash
set -euo pipefail

read -r -p "Administrator username: " username
read -r -s -p "Administrator password: " password
printf '\n'
read -r -s -p "Confirm password: " confirm_password
printf '\n'
if [[ -z "$username" || "$password" != "$confirm_password" ]]; then
  echo "Username is required and the passwords must match." >&2
  exit 1
fi
unset confirm_password
printf '%s\n%s\n' "$username" "$password" | docker compose exec -T backend \
  java -jar /app/app.jar --spring.main.web-application-type=none --app.bootstrap-admin.enabled=true
unset password
