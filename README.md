# GZBGYL CRM

Enterprise CRM foundation for system integrators and software companies to manage opportunities, teams, permissions, audit logs, attachments, and administration.

## Prerequisites

- Docker Desktop with Compose v2
- JDK 21 and Maven 3.9+ for backend development
- Node.js 22 and Corepack/pnpm for frontend development

## Local Deployment

1. Copy the environment template:

   ```bash
   cp .env.example .env
   ```

2. Replace every `CHANGE-ME-UNSAFE-*` value before any real deployment. The example `INITIAL_ADMIN_PASSWORD=Admin#ChangeMe123` is only for local smoke testing.

3. Build and start the full stack:

   ```bash
   docker compose up --build
   ```

4. Open `http://127.0.0.1:8081/login` and sign in with:

   ```text
   username: admin
   password: Admin#ChangeMe123
   ```

The backend health endpoint is proxied through the frontend at `http://127.0.0.1:8081/actuator/health`.

## Development Commands

Backend:

```bash
cd backend
mvn verify
```

Frontend:

```bash
cd frontend
corepack enable
pnpm install --frozen-lockfile
pnpm test:run
pnpm build
pnpm e2e --project=chromium
```

Compose validation:

```bash
docker compose config --quiet
```

## Backup And Restore

Create a database backup:

```bash
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > crm.backup
```

Restore a database backup into an empty database:

```bash
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < crm.backup
```

Attachment files are stored in the `minio-data` Docker volume. Back up that volume together with the database to preserve file metadata and object contents consistently.

## Private Deployment Notes

- Put the stack behind TLS before setting `SESSION_COOKIE_SECURE=true` and `CSRF_COOKIE_SECURE=true`.
- Rotate `INITIAL_ADMIN_PASSWORD` after the first administrator signs in.
- Keep Postgres, Redis, MinIO, and backend services on the private Compose network; expose only the frontend reverse proxy.
- Review `.env` secrets before every deployment and never commit a real `.env` file.
