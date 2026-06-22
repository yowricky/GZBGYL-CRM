#!/bin/sh
set -eu

: "${CRM_APP_PASSWORD:?CRM_APP_PASSWORD is required}"

psql --set=ON_ERROR_STOP=1 \
  --host "${PGHOST:-postgres}" \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=crm_app_password="$CRM_APP_PASSWORD" <<'SQL'
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'crm_app') THEN
        CREATE ROLE crm_app LOGIN;
    END IF;
END
$$;
ALTER ROLE crm_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD :'crm_app_password';
SQL
