#!/usr/bin/env bash
# Daily logical backup of the database, kept on the VM for 14 days and, when
# BACKUP_CONTAINER_URL is set in .env, uploaded to Azure Blob storage.
# Cron (as deploy): 15 3 * * * bash /opt/familybudget/backup.sh >> /opt/familybudget/backups/backup.log 2>&1
set -euo pipefail
cd /opt/familybudget
mkdir -p backups
name="familybudget-$(date +%F).dump"
docker compose exec -T db pg_dump -U familybudget -Fc familybudget > "backups/$name"
find backups -name '*.dump' -mtime +14 -delete

url=$(sed -n 's/^BACKUP_CONTAINER_URL=//p' .env)
if [[ -n "$url" ]]; then
  curl -fsS -X PUT -H 'x-ms-blob-type: BlockBlob' --data-binary "@backups/$name" \
    "${url%%\?*}/$name?${url#*\?}"
fi
echo "$(date -Is) backup $name done"
