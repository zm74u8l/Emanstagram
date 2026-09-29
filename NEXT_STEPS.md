# Next steps: everything you need to do by hand

The code is done. What's left needs your accounts (Cloudflare, Railway,
Vercel, Supabase), so only you can do it. Work top to bottom: later steps
need values from earlier ones. Tick each box as you go.

You'll collect a handful of values along the way. Keep them somewhere safe
(a password manager, not a file in this repo):

| Value | From step |
|---|---|
| R2 endpoint, access key ID, secret access key | 2 |
| Public media URL | 2 |
| Turnstile site key and secret key | 3 |
| JWT secret | 5 |
| Railway backend URL | 5 |
| Vercel frontend URL | 6 |

---

## 1. Clear out the test data (Supabase)

The database still holds accounts and photos from development and testing.
They'd show up on your live site.

- [ ] Supabase dashboard → **SQL Editor**. Preview what's there first:
  ```sql
  SELECT username, email, created_at FROM users ORDER BY created_at;
  ```
- [ ] Delete the demo and test accounts. Their posts, comments, likes,
      stories and follows go with them. Chats don't, so the second
      statement removes chats that no longer have anyone in them:
  ```sql
  DELETE FROM users
  WHERE username LIKE 'demo\_%'
     OR username ~ '^(livea|liveb|smoke)[0-9]+$';

  DELETE FROM conversations c
  WHERE NOT EXISTS (SELECT 1 FROM conversation_members m WHERE m.conversation_id = c.id);
  ```
  Delete any older test accounts of your own too (for example the `emma…`
  ones, if they're yours).
- [ ] **Storage** → open each bucket (`posts`, `avatars`, `stories`,
      `messages`) → select everything → **Delete**. After step 2 you won't
      use these buckets any more, but emptying them keeps things tidy.

**Check:** `SELECT count(*) FROM users;` returns only accounts you want to keep.

---

## 2. Media storage on Cloudflare R2

No egress fees, so image traffic can't run up a bill. The free tier covers 10 GB of storage.

- [ ] Create a free account at [dash.cloudflare.com](https://dash.cloudflare.com) if you don't have one.
- [ ] Sidebar → **R2 Object Storage** → enable it. It may ask for a card
      even for the free tier.
- [ ] **Create bucket** → name it `emanstagram-media`.
  - [ ] Open it → **Settings** → **Public access**. Either:
    - **Custom domain** (recommended, needs a domain added to Cloudflare):
      connect something like `media.yourdomain.com`, or
    - **R2.dev subdomain** → **Allow** (fine to start with; Cloudflare
      rate-limits it, so move to a custom domain before real traffic).
  - [ ] Write down that URL. It's your **public media URL**.
- [ ] **Create bucket** → name it `emanstagram-private`. Leave it private.
      (Chat attachments go here; they're only ever reachable through
      temporary signed links.)
- [ ] R2 overview → **Manage API tokens** → **Create API token**:
  - Permissions: **Object Read & Write**
  - Apply to: **specific buckets** → both of the above
  - [ ] Write down the **Access Key ID**, **Secret Access Key** and the
        **S3 endpoint** (`https://<account-id>.r2.cloudflarestorage.com`).
        The secret is only shown once.

**Check:** both buckets are listed, and opening your public media URL in a
browser doesn't give a "not found" for the domain itself (an empty bucket
may show an error page, which is fine).

---

## 3. Bot check on sign-up (Cloudflare Turnstile)

Stops scripts mass-creating accounts. Free.

- [ ] Cloudflare dashboard → **Turnstile** → **Add widget**.
  - Name: `Emanstagram`
  - Hostnames: your Vercel domain (e.g. `emanstagram.vercel.app`). If you
    don't know it yet, finish step 6 and come back to add it; add your
    custom domain too if you'll use one.
  - Widget mode: **Managed**
- [ ] Write down the **Site Key** and **Secret Key**.

**Check:** the widget is listed with your hostname.

> To test sign-up locally without real keys, put Cloudflare's test keys in
> `backend/.env`: `TURNSTILE_SITE_KEY=1x00000000000000000000AA` and
> `TURNSTILE_SECRET_KEY=1x0000000000000000000000000000000AA`. They always pass.

---

## 4. Spending alarms

Limits in the app stop one account filling your storage; these catch
anything else before it becomes a big bill.

- [ ] **Cloudflare**: dashboard → **Notifications** → add a billing /
      usage alert for R2 (e.g. when storage passes 8 GB, before the 10 GB
      free tier runs out).
- [ ] **Railway** (after step 5): workspace → **Usage** → set a usage
      limit (e.g. $10/month). A hard limit stops the service rather than
      overspending.

---

## 5. Backend on Railway

- [ ] Generate a JWT secret. Run this in Git Bash and copy the output:
  ```bash
  openssl rand -base64 48
  ```
  Don't reuse the one in your local `.env`.
- [ ] [railway.app](https://railway.app) → **New Project** → **Deploy from
      GitHub repo** → pick `Emanstagram`.
- [ ] Service → **Settings** → **Root Directory**: `backend`. Railway
      picks up `backend/Dockerfile`.
- [ ] Service → **Variables**: add all of these.

  | Variable | Value |
  |---|---|
  | `DATABASE_URL` | same as your local `backend/.env` |
  | `DATABASE_USERNAME` | same as local |
  | `DATABASE_PASSWORD` | same as local |
  | `DB_POOL_SIZE` | `5` |
  | `DB_PREPARE_THRESHOLD` | `0` |
  | `JWT_SECRET` | the new one you just generated |
  | `STORAGE_PROVIDER` | `s3` |
  | `S3_ENDPOINT` | from step 2 |
  | `S3_REGION` | `auto` |
  | `S3_ACCESS_KEY_ID` | from step 2 |
  | `S3_SECRET_ACCESS_KEY` | from step 2 |
  | `S3_PUBLIC_BUCKET` | `emanstagram-media` |
  | `S3_PRIVATE_BUCKET` | `emanstagram-private` |
  | `S3_PUBLIC_BASE_URL` | your public media URL from step 2 (no trailing `/`) |
  | `TURNSTILE_SITE_KEY` | from step 3 |
  | `TURNSTILE_SECRET_KEY` | from step 3 |
  | `TRUSTED_PROXY_HOPS` | `1` |
  | `CORS_ORIGINS` | leave for now; set in step 7 |

- [ ] **Settings** → **Networking** → **Generate Domain**. Write down the
      URL (e.g. `https://emanstagram-production.up.railway.app`).
- [ ] **Settings** → **Healthcheck Path**: `/actuator/health`.
- [ ] Deploy and open the **Deploy Logs**. Look for:
  - `Media storage: S3-compatible at …r2.cloudflarestorage.com`
  - **no** "Turnstile is not configured" warning
  - `Started EmanstagramApplication`

**Check:** `https://<railway-url>/actuator/health` shows `{"status":"UP"}`.

---

## 6. Frontend on Vercel

- [ ] [vercel.com](https://vercel.com) → **Add New** → **Project** →
      import `Emanstagram`.
- [ ] **Root Directory**: `frontend` (Vercel detects Vite; `vercel.json`
      handles the rest).
- [ ] **Environment Variables**: `VITE_API_URL` = your Railway URL, with no
      trailing `/`.
- [ ] Deploy. Write down the URL (e.g. `https://emanstagram.vercel.app`).
- [ ] If you didn't have this URL in step 3, add it to the Turnstile
      widget's hostnames now.

---

## 7. Connect the two

- [ ] Railway → **Variables** → `CORS_ORIGINS` = your Vercel URL. Add a
      custom domain too, comma-separated, no spaces:
      `https://emanstagram.vercel.app,https://yourdomain.com`
- [ ] Redeploy the backend.

> Vercel **preview** deployments get their own URLs, which aren't in
> `CORS_ORIGINS`, so previews can't reach the backend. Only the URLs you
> list will work.

---

## 8. Check the proxy setting

Rate limits count requests per IP address. If this is wrong, every user
shares one counter and a single busy person locks everyone out.

- [ ] Open `https://<railway-url>/api/public/ip` in your browser.
- [ ] Compare with your real public IP at [ifconfig.me](https://ifconfig.me).
  - **Same address:** correct, you're done.
  - **An internal address** (`10.…`, `100.64…`, `172.16–31…`, `192.168…`):
    set `TRUSTED_PROXY_HOPS=2`, redeploy, check again.
  - **Never** set it higher than needed: each extra hop trusts one more
    entry that a client could fake.

---

## 9. Try everything on the live site

- [ ] Sign up. There may be a brief "verifying you're human" moment.
- [ ] Post a photo. Right-click it → open image in new tab: the URL starts
      with your **public media URL** (R2), not `supabase.co`.
- [ ] Settings → **Password & sessions**: the storage meter shows your usage.
- [ ] Open the site in a second browser (or a private window), sign up a
      second account, and message between the two: messages and "typing…"
      appear live.
- [ ] Send a photo in chat, and check it loads for the other person.

---

## 10. Make yourself the admin

- [ ] Supabase → **SQL Editor** (use the username you signed up with):
  ```sql
  UPDATE users SET role = 'ADMIN' WHERE username = 'your_username';
  ```
- [ ] Reload the site → **More** → **Moderation**. You'll see reports and
      the **Accounts** tab (sort by storage, newest, suspended; suspend or
      unsuspend people).

---

## 11. Keep it awake

Supabase pauses free projects after 7 days without activity.

- [ ] [uptimerobot.com](https://uptimerobot.com) (free) → **New monitor**
      → HTTP(s) → `https://<railway-url>/actuator/health`, every 5 minutes.
      The health check touches the database, which keeps Supabase awake too.

---

## 12. Tidy up (optional)

- [ ] Delete the local backup branch from the commit-history rewrite:
  ```bash
  git branch -D backup/before-attribution-rewrite
  ```
- [ ] Custom domain: add it in Vercel (frontend) and, if you like, Railway
      (backend). Then add it to `CORS_ORIGINS` and the Turnstile hostnames.
- [ ] Remove `SUPABASE_URL` / `SUPABASE_SECRET_KEY` from your local
      `.env` and set `STORAGE_PROVIDER=s3` with the R2 values, so local dev
      matches production.

---

## Good to know

- **Limits you can change** (Railway variables, all optional):
  `STORAGE_QUOTA_BYTES` (1 GB), `DAILY_UPLOAD_BYTES` (200 MB),
  `DAILY_UPLOAD_COUNT` (50), and the `NEW_ACCOUNT_…` versions for a
  user's first day. See `backend/.env.example`.
- **What resets on a restart:** rate-limit counters and the daily upload
  count. Storage quotas and suspensions are in the database and don't reset.
- **If something breaks:** Railway → Deploy Logs shows the backend's
  errors. Every error the app returns includes a `traceId` that matches a
  line in those logs.
