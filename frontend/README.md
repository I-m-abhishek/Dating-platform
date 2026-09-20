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
