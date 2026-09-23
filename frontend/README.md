# Frontend

Next.js 15 (App Router), TypeScript, Tailwind, TanStack Query, Zustand.

## Run it

```bash
npm install
cp .env.example .env.local     # only if the API is not on localhost:8080
npm run dev
```

http://localhost:3000. The backend must be running.

```bash
npm run build       # production build
npm run typecheck   # tsc --noEmit
npm run lint
```

## Layout

```
src/
├── app/          routes only; pages are thin and each one is wrapped in HOCs
├── components/   ui/, layout/, profile/, discovery/, chat/, paywall/
├── hoc/          withAuth, withGuest, withEntitlement, withErrorBoundary, withQueryState
├── lib/
│   ├── api/      client, endpoints, types, errors, tokenStore, queryKeys
│   ├── hooks/    one per domain
│   ├── stores/   authStore, uiStore
│   ├── ws/       the single STOMP connection
│   └── utils/
└── providers/    QueryProvider, SessionProvider, AppProviders
```

Pages use hooks, hooks use endpoints, endpoints use the client. No component builds a URL.

## The HOCs

```tsx
export default compose(withErrorBoundary, withAuth)(MatchesPage);
```

Outermost first, and the order matters: the boundary wraps the guard so a crash during the
auth check is still caught, and an entitlement check must sit inside the auth check because
you need a signed-in user before you can ask what they are entitled to.

`withAuth` is a HOC rather than a hook on purpose. A hook runs inside the component, so the
protected UI would mount and fire its queries before the redirect — a flash of private
chrome and a burst of 401s. Wrapping lets the guard return a loader instead.

See [`../docs/frontend.md`](../docs/frontend.md) for the rest.

## Environment

| Variable | Default |
| --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` |
| `NEXT_PUBLIC_WS_URL` | `http://localhost:8080/ws` |
| `BACKEND_URL` | `http://localhost:8080` - where the Next server proxies `/api/v1` and `/ws` |

## Testing on a phone

Browsers only give the microphone and camera to **secure** pages. On iOS, a page served over
plain `http://192.168.x.x:3000` does not even have `navigator.mediaDevices`, so calls show
"Calls need a secure connection". Serve the app over HTTPS:

1. `.env.local` must use same-origin mode (both public URLs empty; see `.env.example`), so the
   browser only talks to the Next server, which proxies `/api/v1` and `/ws` to `BACKEND_URL`.
   This also keeps photos loading, because an https page may not load `http://` resources.
2. Create a certificate for your LAN IP (Git Bash, needs `openssl`; once per IP change):
   ```
   npm run cert -- 192.168.1.3
   ```
3. Start the dev server over HTTPS (instead of `npm run dev`):
   ```
   npm run dev:https
   ```
4. **One-time iPhone setup**, so Safari trusts the certificate (secure WebSockets fail on a
   certificate you have only clicked past):
   1. In Safari open `https://192.168.1.3:3000/dev-ca.crt`. Tap past the warning; Safari
      says a profile was downloaded.
   2. Settings -> General -> VPN & Device Management -> "Two and Two Dev CA" -> Install.
   3. Settings -> General -> About -> Certificate Trust Settings -> turn on "Two and Two Dev CA".
5. Open `https://192.168.1.3:3000` on the phone. On the computer use `https://localhost:3000`.

If your IP is not `192.168.1.3`, pass it to `npm run cert`, set `DEV_LAN_HOST` for the
frontend, and `DEV_LAN_ORIGIN_HTTPS=https://<ip>:3000` for the backend.

A tunnel also works and needs no certificate: `npm run dev` plus
`cloudflared tunnel --url http://localhost:3000`, then open the `https://…trycloudflare.com`
address it prints.

System notifications (for when the tab is in the background) are requested on the first tap.
On iPhone, Safari only supports web notifications for sites added to the Home Screen
(iOS 16.4+).
