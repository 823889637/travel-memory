#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/.." && pwd)"
auth_dir="$project_root/deploy/secrets"
auth_file="$auth_dir/.htpasswd"

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required to create an Nginx-compatible password hash. Install it with your system package manager and run this script again." >&2
  exit 1
fi

read -r -p "Basic Auth username: " username
if [[ -z "$username" || "$username" == *:* || "$username" == *$'\n'* || "$username" == *$'\r'* ]]; then
  echo "Username must not be empty or contain ':' or line breaks." >&2
  exit 1
fi

read -r -s -p "Basic Auth password: " password
echo
read -r -s -p "Confirm password: " password_confirmation
echo

if [[ -z "$password" ]]; then
  echo "Password must not be empty." >&2
  exit 1
fi

if [[ "$password" != "$password_confirmation" ]]; then
  echo "Passwords do not match." >&2
  exit 1
fi

if [[ -e "$auth_file" ]]; then
  read -r -p "$auth_file already exists. Overwrite it? [y/N] " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "No changes made."
    exit 0
  fi
fi

umask 077
mkdir -p "$auth_dir"
chmod 700 "$auth_dir"
password_hash="$(printf '%s\n' "$password" | openssl passwd -apr1 -stdin)"
temp_file="$(mktemp "$auth_dir/.htpasswd.XXXXXX")"
trap 'rm -f "$temp_file"; unset password password_confirmation password_hash' EXIT
printf '%s:%s\n' "$username" "$password_hash" > "$temp_file"
chmod 600 "$temp_file"
mv -f "$temp_file" "$auth_file"
# Nginx workers run as an unprivileged user and must read the bind-mounted hash.
# The parent directory remains owner-only, so other host users cannot traverse it.
chmod 644 "$auth_file"

echo "Created $auth_file (directory 700, file 644). Keep it out of Git."
