#!/usr/bin/env bash
# Usage: deploy.sh <api|web> <image-tag>. Called by the CI deploy jobs of both repos.
# Pins the tag in .env, then starts the service and its dependencies, recreating whatever
# changed. The backend can go first, before any frontend image exists.
# Rollback: run it again with an older tag (a git SHA).
set -euo pipefail
cd /opt/familybudget
exec 9>.deploy.lock
flock 9 # both repos may deploy at the same time

case "${1:-}" in
  api) var=API_TAG ;;
  web) var=WEB_TAG ;;
  *) echo "usage: deploy.sh <api|web> <image-tag>" >&2; exit 1 ;;
esac
[[ "${2:-}" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "invalid image tag: ${2:-}" >&2; exit 1; }

sed -i "s/^$var=.*/$var=$2/" .env
docker compose pull "$1"
docker compose up -d "$1"
docker image prune -f
