# Project Status

**Last updated:** 27 September 2026
**Commit:** `f03ce22` — *fix: repair truncated migration and switch users off citext*

A rebuild of the original Flask Instagram clone on a modern stack, with
real chat, real profiles, and media stored outside the database.

---

## TL;DR

| | |
|---|---|
| **Phase** | Infrastructure + auth complete. Feature work not started. |
| **Database** | ✅ Live on Supabase. 19 tables created and validated. |
| **Storage** | ✅ 4 buckets live on Supabase. |
| **Auth** | ✅ Working end-to-end against real Postgres. |
| **Posts / chat / profiles** | ❌ Schema ready, **no endpoints written yet**. |
| **Tests** | 12 passing on the H2 profile. |
| **Deployed** | ❌ Local only. Never deployed. |

**The one-line summary:** everything needed to *start* building features
works and is verified. Nothing that a user would recognise as the app
exists yet.

---

## How to run it

```bash
# Backend — real Supabase
cd backend
../tools/apache-maven-3.9.16/bin/mvn spring-boot:run

# Backend — zero-setup local (H2, no cloud)
../tools/apache-maven-3.9.16/bin/mvn spring-boot:run \
    -Dspring-boot.run.profiles=local

# Frontend
cd frontend
npm run dev            # http://localhost:5173
```

Vite proxies `/api` and `/ws` to `:8080`, so no CORS setup is needed in dev.

---

## What is done

### Infrastructure — verified working

- **Toolchain.** Maven vendored under `tools/`, so no global install.
  Java 21.0.11 and Node 22.20 confirmed working.
- **Database schema live on Supabase.** `V1__init.sql` applies cleanly:
  19 tables, 6 enums, case-insensitive unique indexes. Flyway reports
  `Schema "public" is up to date` and `ddl-auto: validate` passes, so
  the JPA entities and the real schema are confirmed in agreement.
- **Storage buckets live.** `posts`, `avatars`, `stories` public;
  `messages` private for signed URLs. Verified via the Storage API.
- **CI.** GitHub Actions runs `mvn test` and `tsc -b` on every push.
- **SPA deep-link forwarding.** `/u/someone` survives a hard refresh.

### Authentication — complete and tested

- Register, login (by username **or** email), refresh, logout,
  logout-everywhere.
- **JWT access token** — 15 min, stateless, Argon2id hashed.
- **Refresh token** — opaque 256-bit random string, **rotated on every
  use**. Only the SHA-256 hash is stored, so a database leak cannot be
  replayed as a valid session.
- **Reuse detection.** Replaying a revoked refresh token revokes every
  session for that account.
- Login returns an identical error for an unknown user and a wrong
  password, so the endpoint cannot be used to enumerate accounts.

Live test results against real Postgres:

| Check | Result |
|---|---|
| Register | ✅ creates a user, returns tokens |
| `/api/auth/me` with valid token | ✅ |
| No token / tampered token | ✅ 401 |
| Duplicate username | ✅ 409 |
| Weak password | ✅ 400 |
| Login by email | ✅ |
| Refresh rotation | ✅ new pair issued |
| Old refresh replayed | ✅ 401 |

### Media pipeline — built, not yet exercised

- `SupabaseStorageService` with public CDN URLs and signed URLs for DMs.
- `MediaValidationService` enforcing 10 MB images, 15 MB videos,
  5 MB avatars, 20 MB story video.
- Limits enforced in **three** places: client, service, container.
- Limits published to the client at `/api/config/public` so an
  oversized file is rejected before the user spends bandwidth.

> **No upload has ever been performed.** The service is wired up and
> reports `storageEnabled: true`, but there is no upload endpoint yet,
> so this code path is unproven.

### Frontend — shell and design system only

Working:
- Design tokens, light/dark/system themes, per-user accent colour
- Responsive shell: desktop side rail, mobile bottom tab bar
- API client with transparent token refresh
- Session bootstrap, route guarding
- `Button`, `Input`, `Avatar` (with deterministic identicon fallback)
- Login, Register, Settings, NotFound — real forms
- CreatePost — real file picker with live client-side validation

**Placeholder only** (skeleton shimmer, no data):

---

## What is NOT done

### Endpoints — only 8 exist in total

| Endpoint | Status |
|---|---|
| `POST /api/auth/register` | ✅ |
| `POST /api/auth/login` | ✅ |
| `POST /api/auth/refresh` | ✅ |
| `POST /api/auth/logout` | ✅ |
| `POST /api/auth/logout-all` | ✅ |
| `GET /api/auth/me` | ✅ |
| `GET /api/config/public` | ✅ |
| SPA forwarding | ✅ |

**Everything below is schema-only. The tables exist, the Java does not.**

### JPA entities — 2 of 19 tables have them

| Have an entity | Missing an entity |
|---|---|
| `users` | `posts`, `post_media`, `post_likes`, `comments`, |
| `refresh_tokens` | `comment_likes`, `saved_posts`, `follows`, `stories`, |
| | `story_views`, `conversations`, `conversation_members`, |
| | `direct_conversation_keys`, `messages`, `message_receipts`, |
| | `notifications`, `blocks`, `reports` |

### Features — none built

- [ ] **Posts** — create, read, delete, carousels, captions
- [ ] **Media upload** — endpoint, transcoding, thumbnail generation
- [ ] **Likes** — post and comment
- [ ] **Comments** — threaded reads and writes
- [ ] **Follows** — follow/unfollow, follower lists
- [ ] **Feed** — Following vs Explore, cursor pagination, infinite scroll
- [ ] **Chat** — WebSocket/STOMP handler exists but **no logic**.
      Needs DMs, groups, typing indicators, read receipts, presence
- [ ] **Stories** — 24h expiry, view tracking
- [ ] **Notifications** — likes, comments, follows, mentions
- [ ] **Search** — users, hashtags, posts
- [ ] **Moderation** — reports, blocks, mutes, admin queue
- [ ] **Profile editing** — display name, bio, banner, avatar upload
- [ ] **Browser video transcode** — `ffmpeg.wasm` (in-browser 720p
      compression so large source files never upload at full size)
- [ ] **Email verification / password reset** — columns exist, no flow

### Infrastructure — not done

- [ ] **Deployment.** Never deployed. Railway/Fly + Vercel plan is written
      but untested.
- [ ] **Frontend build served by the backend** in production
      (`FRONTEND_DIST`) — code path exists, untested.
- [ ] **CI has never run.** The workflow is committed but the first
      GitHub Actions run is unverified.
- [ ] **No PWA**, no offline support, no push notifications.


---

## Bugs found and fixed

Recorded because they were all invisible until something was actually
executed — which is the pattern worth remembering.

1. **Argon2 without BouncyCastle.** Compiled fine, threw
   `NoClassDefFoundError` on the *first registration*. The try/catch
   fallback was wrong: the encoder constructs OK and only fails on use.
2. **Missing JPA no-arg constructors** after removing Lombok. Hibernate
   couldn't build a proxy, so every authenticated request 401'd.
3. **`trim('/')` doesn't exist in Java** — compiles only for a `char`.
4. **Spring Security rejects capture groups** in `requestMatchers`
   regexes, so the SPA fallback 500'd on every route.
5. **`.env` was never read.** Spring Boot does not read `.env` files
   unless you opt in via `spring.config.import`. A correctly filled-in
   file was silently ignored.
6. **Storage config discarded by the local profile.** A profile-specific
   YAML file *replaces* whole keys rather than merging, so
   `application-local.yml` shadowed the real values. The Supabase key was
   never read, however it was configured.
7. **`V1__init.sql` was silently truncated** — the `comments` table lost
   its body mid-definition. Also had duplicate table blocks and a stray
   `);`. This schema had **never been executed** before the first real
   Supabase run.
8. **`citext` is incompatible with Hibernate 6.6** — no JavaType exists,
   so `ddl-auto: validate` refused to start. Switched to `varchar` plus
   case-insensitive unique indexes.

---

## Database schema

19 tables. Media bytes are never stored here — only Supabase Storage
object keys.

```
Identity    users, refresh_tokens
Social      follows, blocks
Content     posts, post_media, post_likes, comments, comment_likes,
            saved_posts
Ephemeral   stories, story_views
Chat        conversations, conversation_members,
            direct_conversation_keys, messages, message_receipts
Safety      notifications, reports
```

6 enums: `post_visibility`, `post_kind`, `conversation_kind`,
`message_kind`, `notification_kind`, `report_reason`

### Design notes worth keeping

- **Media never enters the database.** This is the core fix. The
  original stored base64 in SQLite — a 23 MB file holding 2 users and
  11 posts.
- **`direct_conversation_keys`** guarantees a DM holds exactly two
  people and prevents duplicate DM rows for the same pair.
- **Threaded comments** via self-referencing `comments.parent_id`.
- **Denormalised counters** (`follower_count` etc.) maintained with
  atomic bulk updates so concurrent follows don't lose writes.

---

## Media limits

| Asset | Cap | Stored as |
|---|---|---|
| Post image | 10 MB | 2048px WebP (~150–200 KB) |
| Post video | **15 MB** | 720p H.264, streamed |
| Avatar | 5 MB | 400×400 WebP |
| Story | 10 MB image / 20 MB video | 1080×1920 |
| Chat attachment | 15 MB | as above |

15 MB is roughly 60 seconds of video — Instagram-story length. A 50 MB
cap would be ~3.5 minutes, which is more than people watch, and would
cut free-tier capacity by two thirds.

---

## Deployment plan (untested)

| Piece | Host | Cost |
|---|---|---|
| Backend | Railway or Fly.io | ~$5/mo |
| Frontend | Vercel or Cloudflare Pages | Free |
| Database | Supabase Postgres | Free tier |
| Media | Supabase Storage | 1 GB free |
| Keepalive | UptimeRobot | Free |

**Not Vercel/Lambda for the backend** — serverless kills long-lived
WebSocket connections, which chat requires.

> ?? **Egress, not storage, is the real limit.** The free tier includes
> 5 GB of bandwidth. At ~12 posts × 200 KB that's roughly 2,000 feed
> loads. Plan for the $25 Pro tier before going public.

---

## Supabase connection (currently in use)

Free-tier projects are IPv6-only for direct connections, so the pooler is
required:

```
DATABASE_URL=jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require
DATABASE_USERNAME=postgres.<project-ref>
DB_PREPARE_THRESHOLD=0
```

Port 5432 is **session mode**, which supports prepared statements.
`DB_PREPARE_THRESHOLD` is `0` in `.env`; setting it to `-1` re-enables
them since session mode allows it.

---

## Suggested next steps

In rough priority order:

1. **Post + media upload endpoints** — the biggest single gap. Everything
   else (likes, comments, feed) depends on posts existing.
2. **Real upload test** — the storage code has never actually run.
3. **Feed + likes + comments** — completes the core loop.
4. **Profile editing** — the feature that makes it feel personal.
5. **Chat over WebSocket** — the headline feature, but the most work.
6. **Deploy** — once there's something worth showing.

---

## Working practice note

Every bug in this list was found by **running** something, not by
reading it. Several would have shipped silently:

- Tests all passed while the database schema was fundamentally broken
- The app started happily while discarding the Supabase key entirely
- `mvn test` was green while a required runtime dependency was missing

Treat any claim of "this should work" as a hypothesis until it has been
observed to work.


