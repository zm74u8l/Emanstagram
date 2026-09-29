# Project Status

**Last updated:** 28 September 2026
**Branch:** `feat/full-app`

A rebuild of the original Flask Instagram clone on a modern stack, with
real chat, real profiles, and media stored outside the database.

---

## TL;DR

| | |
|---|---|
| **Phase** | Feature-complete for the agreed scope. Not deployed. |
| **Database** | ✅ Supabase, schema at V2. Every table except `message_receipts` is mapped and validated. |
| **Storage** | ✅ Two providers behind one interface: Supabase Storage (verified live) and S3-compatible for Cloudflare R2 (verified end to end against an S3 server; not yet against R2 itself). |
| **Backend** | ✅ ~70 endpoints + STOMP WebSocket. 46 automated tests passing. |
| **Frontend** | ✅ Redesigned. Every page is real, driven in Chromium against the live backend. |
| **Abuse protection** | ✅ Storage quota, daily upload budget, rate limits, Turnstile on sign-up, file-content checks, account suspension. Verified on Supabase and in the browser. |
| **Deployed** | ❌ Local only. See [NEXT_STEPS.md](NEXT_STEPS.md). |

---

## How to run it

```bash
# Backend: real Supabase (reads backend/.env)
cd backend
../tools/apache-maven-3.9.16/bin/mvn spring-boot:run

# Backend: zero-setup local (in-memory H2; storage still uses .env if present)
../tools/apache-maven-3.9.16/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local

# Frontend
cd frontend
npm run dev            # http://localhost:5173
```

Vite proxies `/api` and `/ws` to `:8080`.

### Demo data on the Supabase project

The verification runs left these accounts in the live database (all use
the password `demo-password-1`):

- `demo_you` (sign in as this one), `demo_mara`, `demo_jon`, `demo_ines`,
  `demo_kofi`: profiles, posts, comments, stories, a DM, a group chat and
  one open report
- `livea…`, `liveb…`, `smoke…`: throwaway accounts from API checks

Delete them from the Supabase dashboard (or `DELETE FROM users WHERE
username LIKE 'demo\_%' ...`; the FKs cascade) when they're no longer
useful. Their media stays in the buckets until removed.

### Making someone a moderator

There is no UI for granting roles, by design:

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'your_name';  -- or MODERATOR
```

The role is read from the database on every request, so it applies
immediately. Moderators see **More → Moderation** (`/admin`).

---

## What is done

### Backend

| Area | Endpoints / behaviour |
|---|---|
| Auth | register, login, refresh (rotating, reuse detection), logout, logout-all, me, username availability, change password (revokes other sessions) |
| Posts | multipart create (1-10 images/videos), get, edit caption/location/visibility, delete (author or moderator) |
| Likes & saves | idempotent `PUT`/`DELETE`, likers list, Saved collection |
| Comments | threaded (kept two levels deep), replies, comment likes, delete by comment author / post author / moderator |
| Social | follow, unfollow, remove follower, block (severs follows both ways), blocked list, followers/following lists, suggestions |
| Profiles | view (with follow/block state), `PATCH /api/me`, avatar and banner upload/remove |
| Feed | home (followed + self), explore, exact-match hashtag pages, people/tag search, public login mosaic |
| Notifications | like, comment, reply, follow, mention; deduped, retracted on unlike/unfollow, pushed live |
| Chat | DMs (race-safe get-or-create), groups (add, leave, remove, ownership handover), text + image/video attachments, replies, edit, soft delete, read receipts, unread counts, presence, typing |
| Stories | create, tray (own first, unseen next), per-user, view tracking, viewer list, delete, hourly purge |
| Moderation | report user/post/comment (duplicate-safe), moderator queue, dismiss or remove content |

Key design points:

- **V2 migration** converts the six native Postgres ENUMs to VARCHAR +
  CHECK, the same fix `citext` needed in V1.
- **Keyset pagination** on `(created_at, id)` everywhere, no OFFSET.
- **Counters** move via atomic bulk updates, and likes/follows/saves use
  `INSERT … ON CONFLICT DO NOTHING`, so double-taps and races can't skew
  them.
- **`AccessPolicy`** is the single place visibility (PUBLIC / FOLLOWERS /
  PRIVATE) and blocks are decided.
- **WebSocket**: native STOMP at `/ws`; the JWT is checked on CONNECT and
  subscriptions are limited to the caller's own `/user/queue/*`.
  Everything arrives on one `/user/queue/events` stream as
  `{ type, data }`, sent only after the DB commit.
- **Storage fixes** found before first use: the `apikey` header that
  `sb_secret_` keys need, RestClient encoding `/` as `%2F`, and signing
  moved to the batch `POST` endpoint with a cache.

### Frontend

Quiet monochrome design: ink on paper, Geist for the UI, Instrument Serif
for the wordmark, the user's accent colour only for signals. Everything
below is wired to the API:

Login/register (live photo mosaic, username check) · feed with stories
rail · post cards (carousel, blurhash, double-tap like) · post detail
with threaded comments · composer (multi-file, reorder, **in-browser
WebP resize**, upload progress) · story composer and viewer · explore +
search · hashtag pages · profiles, followers/following · saved ·
messages (DMs, groups, attachments, typing, Seen, presence, edit/delete)
· activity · settings (profile, avatar/banner, appearance synced to the
account, password, sessions, blocked) · moderation queue · 404.

---

## How it was verified

| Check | Result |
|---|---|
| `mvn test` (H2, MockMvc, storage mocked) | ✅ 46 passing, in any class order, and on GitHub Actions (Linux) |
| Real STOMP socket test (auth refused without token, live delivery, foreign subscription rejected) | ✅ |
| Flyway V2 on Supabase + `ddl-auto: validate` | ✅ |
| Live API pass against Supabase | ✅ 38/38, including signed URLs on the private bucket, and confirming that bucket is *not* publicly readable |
| First real upload → CDN serves identical bytes → delete removes the object | ✅ |
| `tsc -b` + `npm run build` | ✅ |
| Two Chromium users: typing indicator, live message, Seen, live reply, UI upload stored as WebP, live mention badge, like/comment persisted, delete via UI | ✅ 12/12, no console errors |
| Screens at 1440 px and 390 px, light and dark | ✅ reviewed by eye |

Not exercised by hand: the moderation queue UI (API tested; needs a
promoted account), video playback in stories and chat, and anything on a
real phone rather than an emulated viewport.

---

## Abuse protection

| Layer | What it does |
|---|---|
| Storage quota | 1 GB per account, totalled from the database on every upload (survives restarts) |
| Daily upload budget | 200 MB / 50 files per rolling 24 h, 50 MB / 15 files for accounts under a day old; counts deleted uploads so upload-delete loops don't escape it |
| Rate limits | Sign-ups 5/h and logins 10/15 min per IP; 20 login attempts per account per 15 min; per-account limits on uploads, comments, messages, follows, likes, reports, search; 600 req/min per IP overall. 429 + `Retry-After` |
| Bot check | Cloudflare Turnstile on sign-up once `TURNSTILE_*` keys are set; fails closed |
| File contents | Type read from the file's first bytes, never the client's label; the detected type is what's stored |
| Suspension | Blocks sign-in, refresh, every API call and the socket at once; can wipe the account's posts and stories |

In memory (resets on restart, fine for one instance): rate-limit counters
and the daily upload ledger. In the database (permanent): quota usage and
suspensions. Several backend instances would need Redis for the former.

## Deliberately out of scope

- **Private accounts / follow requests.** `users.is_private` exists but
  isn't exposed; it needs a `follow_requests` table.
- **Email verification / password reset.** Needs an email provider.
- **In-browser video compression (ffmpeg.wasm).** Videos over 15 MB are
  rejected with a clear message rather than compressed.
- **`message_receipts`** has no entity. Read state uses
  `conversation_members.last_read_at` instead, which is enough for
  "Seen" and unread counts.

## Still to do

- [ ] **Deploy.** Every manual step is in [NEXT_STEPS.md](NEXT_STEPS.md).
- [ ] Hashtags are read out of captions (fine at this scale); a
      `post_tags` table would be the upgrade path.
- [ ] Presence and the STOMP broker are in-memory, which is correct for
      one instance. More than one needs a broker relay and shared presence.
- [ ] No PWA, push notifications or offline support.

---

## Bugs found and fixed

Every one of these was found by **running** something, not by reading
code.

1. **Argon2 without BouncyCastle.** Threw `NoClassDefFoundError` on the first registration.
2. **Missing JPA no-arg constructors** made every authenticated request 401.
3. **`trim('/')` doesn't exist in Java.**
4. **Spring Security rejects capture groups** in `requestMatchers`.
5. **`.env` was never read** until `spring.config.import` was added.
6. **Storage config discarded by the local profile.**
7. **`V1__init.sql` was silently truncated.**
8. **`citext` is incompatible with Hibernate 6.6.**
9. **Native ENUMs have the same Hibernate problem.** Fixed by V2.
10. **Storage requests would have failed on first use:** missing `apikey`
    header, `/` double-encoded in object keys, and the sign call used
    `GET` where Supabase needs `POST`.
11. **WebSocket user prefix was `/user/queue`**, which breaks
    `convertAndSendToUser`, and SockJS plus a raw endpoint were both
    registered on `/ws`.
12. **`UUID.compareTo` disagrees with Postgres UUID ordering** for about
    half of all pairs, which would trip the `user_low < user_high` check
    on DMs. Pairs are now ordered by their hex strings, with a regression
    test.
13. **Safe-area padding zeroed the chat composer's bottom padding**
    (a later CSS rule won). Only visible in a screenshot.
14. **Tests passed on Windows and failed on CI.** Every test context
    shared one in-memory H2 database, and a closing context's
    `create-drop` removed the tables under the others. Whether that hurt
    depended on class order, which differs between filesystems. Each
    context now gets its own database.
15. **Sign-up could be submitted before the app knew a bot check was
    required**, producing a confusing "complete the check" error. The
    button now waits for the config. Found by the browser test.
