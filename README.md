# Emanstagram

A rebuild of the original Flask Instagram clone on a modern stack, with real
chat, real profiles, and media stored outside the database.

## What changed from the original

The original (`../website-like-insta-kinda`) was a ~220-line Flask app that
stored base64-encoded images directly in SQLite. Its 23 MB database held
**2 users and 11 posts** — roughly 2 MB per post, because base64 inflates
every file by 33% and avatars were denormalised into every post and comment
row.

This version fixes the architecture rather than the styling:

| Concern | Original | Now |
|---|---|---|
| Backend | Flask 2.3, global state | Spring Boot 3.5, DI, layered packages |
| Database | SQLite, blobs inline | PostgreSQL, keys only — no media bytes |
| Media | base64 `TEXT` columns (33% larger) | Supabase Storage + CDN |
| Auth | Flask-Login sessions | JWT access + rotating refresh, Argon2id |
| Uploads | `.jpg/.jpeg/.png` only, unbounded | 3 validated gates, 10 MB images / 15 MB videos |
| Password change | silent no-op (rebound a local) | explicit accessor, tested |
| Username check | dead code, never invoked | pre-check + DB constraint |
| UI | inline `<style>`, gray boxes | React 19, Tailwind v4, theming, responsive |

## Stack

**Backend** — Spring Boot 3.5.16 · Java 21 · Spring Security 6 · Spring Data
JPA · Flyway · PostgreSQL 16 · WebSocket (STOMP)

**Frontend** — React 19 · TypeScript · Vite 6 · Tailwind CSS v4 · TanStack
Query · Zustand · STOMP.js · lucide-react · Geist + Instrument Serif (self-hosted)

**Media** — Cloudflare R2 or any S3-compatible store (`STORAGE_PROVIDER=s3`), or
Supabase Storage (`STORAGE_PROVIDER=supabase`). Public bucket for posts,
avatars and stories; private bucket with signed URLs for DM attachments.

## Layout

```
emanstagram/
├── backend/
│   ├── .env.example              copy to .env and fill in
│   └── src/main/
│       ├── java/com/emanstagram/
│       │   ├── auth/             JWT issue/verify, register/login/refresh
│       │   ├── chat/             DMs, groups, messages, read state
│       │   ├── comment/          threaded comments + likes
│       │   ├── common/           error shape, CurrentUser, cursors
│       │   ├── config/           security, CORS, WebSocket, properties
│       │   ├── feed/             home, explore, hashtags, login mosaic
│       │   ├── moderation/       reports + moderator queue
│       │   ├── notification/     activity feed
│       │   ├── post/             posts, media, likes, saves
│       │   ├── realtime/         STOMP auth, presence, event publisher
│       │   ├── search/           people + hashtag search
│       │   ├── social/           follows, blocks, AccessPolicy
│       │   ├── storage/          Supabase uploads + media validation
│       │   ├── story/            24h stories + cleanup job
│       │   └── user/             User, profiles, profile editing
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           └── db/migration/     V1 schema, V2 enums -> varchar
└── frontend/
    └── src/
        ├── components/           ui/ primitives, post/, chat/, stories/, auth/
        ├── hooks/                theme, realtime bridge, debounce
        ├── layouts/              AppLayout (rail + mobile tab bar)
        ├── lib/                  api, realtime (STOMP), media, cache, types
        ├── pages/                one file per route
        └── stores/               auth, chat (typing/presence), toasts
```

## Running it

### Backend

Maven is vendored under `tools/`, so no global install is needed.

```bash
cd backend

# Zero-setup local dev (in-memory H2, no Postgres, no Supabase)
../tools/apache-maven-3.9.16/bin/mvn spring-boot:run \
    -Dspring-boot.run.profiles=local
```

The `local` profile disables Flyway and lets Hibernate build the schema,
because the migrations use Postgres-specific syntax (`pgcrypto`, partial
indexes, `ALTER ... USING`) that H2 rejects. **The Postgres schema is
only exercised against a real Postgres instance.**

Against Supabase:

```bash
cp .env.example .env      # fill in the values
../tools/apache-maven-3.9.16/bin/mvn spring-boot:run
```

`.env` is read because `application.yml` opts in explicitly:

```yaml
spring:
  config:
    import: "optional:file:.env[.properties]"
```

Spring Boot does **not** read a `.env` file on its own. Without that import a
correctly filled-in file is silently ignored and every secret falls back to
the placeholder defaults. `optional:` keeps the app startable when no `.env`
exists, which is what the `local` profile and CI depend on.
`DotEnvLoadingTests` covers both directions.

### Which connection string to use

Both work; they differ in host, port, and one important behaviour.

| | Direct | Connection pooler |
|---|---|---|
| Host | `db.<ref>.supabase.co` | `aws-0-<region>.pooler.supabase.com` |
| Port | 5432 | 6543 |
| Prepared statements | work | **not supported** |
| Intended for | long-running backends | serverless / many short connections |
| Connection cap (free) | ~15 | much higher |

A long-running Spring Boot server fits the direct connection, so that is the
default in `.env.example`, with `DB_POOL_SIZE=5` to stay well inside the cap.

If you use the pooler instead, leave `DB_PREPARE_THRESHOLD=0`. Transaction mode
returns a connection to the pool after each transaction, which breaks
server-side prepared statements and produces `prepared statement "S_1" already
exists` on the first query. The config sets this to 0 by default so both work.

`?sslmode=require` is mandatory in either case.

### Storage buckets

One-time setup. `posts`, `avatars` and `stories` are public so the CDN can
serve them directly; `messages` is private and only reachable through
short-lived signed URLs.

```powershell
./scripts/create-buckets.ps1 `
    -SupabaseUrl https://<ref>.supabase.co `
    -ServiceRoleKey sb_secret_xxx
```

The script is idempotent, so re-running it is safe.

### Frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Vite proxies `/api` and `/ws` to `:8080`, so there is no CORS setup in dev.

### Tests

```bash
cd backend && ../tools/apache-maven-3.9.16/bin/mvn test
cd frontend && npx tsc -b      # typecheck
cd frontend && npm run build
```

## Media limits

| Asset | Upload cap | Stored as |
|---|---|---|
| Post image | 10 MB | 2048px WebP (~150–200 KB) |
| Post video | **15 MB** | 720p H.264, streamed |
| Avatar | 5 MB | 400×400 WebP |
| Story | 10 MB image / 20 MB video | 1080x1920 |
| Chat attachment | 15 MB | as above |

A 50 MB video is ~3.5 minutes of 1080p. Capping at 15 MB is ~60 seconds,
which matches how long people actually watch, and triples capacity on the
free tier.

**Images are resized in the browser** before upload (longest edge 2048px,
re-encoded as WebP with a blurhash placeholder), so a 4 MB phone photo
typically uploads as ~200 KB. Videos are not transcoded: anything over the
cap is rejected with a clear message.

Limits are enforced in three places:

1. **Client** - validates against `/api/config/public` and resizes images
2. **Server** - `MediaValidationService` returns a specific 4xx with a message
3. **Container** - Spring's `max-file-size` catches anything that slips past

## Auth design

- **Access token** - 15-minute JWT, stateless
- **Refresh token** - opaque 256-bit random string, **rotated on every use**
- Only the SHA-256 hash of a refresh token is stored, so a database leak
  cannot be replayed as a valid session
- Replaying an already-revoked refresh token revokes **every** session for
  that account (reuse detection)
- Argon2id hashing (BouncyCastle is a required dependency - omitting it
  compiles fine but throws `NoClassDefFoundError` on the first registration,
  which is covered by a regression test)
- Login returns the same error for an unknown user and a wrong password, so
  the endpoint cannot be used to enumerate accounts

## Deployment

| Piece | Host | Notes |
|---|---|---|
| Backend | Railway or Fly.io | **not** Vercel/Lambda - serverless kills WebSockets |
| Frontend | Vercel or Cloudflare Pages | free, global CDN |
| Database | Supabase Postgres | via the pooler |
| Media | Supabase Storage | 1 GB free |
| Keepalive | UptimeRobot (free) | pings health every few days |

The backend can also serve the built frontend so one deployable covers both:

```bash
FRONTEND_DIST=file:///path/to/frontend/dist/ mvn spring-boot:run
```

Expected cost: **$0-5/month**.

> Supabase's free tier pauses a project after 7 days of inactivity. A free
> UptimeRobot monitor hitting `/actuator/health` prevents that entirely.

> **Egress, not storage, is the real limit.** The free tier includes 5 GB of
> bandwidth. At ~12 posts x 200 KB, that is roughly 2,000 feed loads. Plan
> for the $25 Pro tier before going public.

## Status

Feature-complete for the agreed scope; not yet deployed. See
[STATUS.md](STATUS.md) for what was verified and how, what is
deliberately out of scope, and what remains.

## Realtime

The browser opens one STOMP connection to `/ws` and authenticates in the
CONNECT frame with its access token (browsers can't set headers on a
WebSocket handshake). Writes go over REST, so validation errors are
ordinary HTTP errors. Every change is then pushed to the people concerned
on `/user/queue/events` as `{ type, data }`: `message`, `typing`, `read`,
`presence`, `notification` and a few more. The frontend's
`useRealtimeBridge` turns those events into React Query cache updates, so
pages don't talk to the socket themselves.

## Moderation

Any user can report an account, post or comment. Moderators and admins
review reports at `/admin`, where they can dismiss a report or remove the
content. Roles are granted in SQL:

```sql
UPDATE users SET role = 'MODERATOR' WHERE username = 'someone';
```

## Notes

- Lombok is deliberately **not** used. Its annotation processor is fragile
  offline, and explicit accessors remove that whole class of build failure.
- `ddl-auto: validate` in production - Flyway owns the schema, Hibernate
  only checks that the entities agree with it.
- Supabase's service role key is server-side only. It must never be prefixed
  `VITE_`, or Vite will inline it into the JS bundle.
