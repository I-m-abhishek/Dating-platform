# Frontend

Next.js 15 App Router, TypeScript in strict mode, Tailwind, TanStack Query for server state,
Zustand for the little that is genuinely client state.

---

## Layout

```
src/
├── app/                        routes only - every page is thin
│   ├── (auth)/                 login, register        (withGuest)
│   ├── (main)/                 the app shell          (withAuth)
│   ├── onboarding/             withAuth, requireOnboarding: false
│   └── layout.tsx              providers mount here
│
├── components/
│   ├── ui/                     Button, Input, Sheet, Avatar, Skeleton, ...
│   ├── layout/                 SideNav, BottomNav, TopBar
│   ├── profile/                PhotoCarousel, ProfileDetails, PhotoCommentSheet
│   ├── discovery/              FeedCardView, FilterSheet
│   ├── chat/                   MessageBubble, MessageComposer, CallOverlay
│   └── paywall/                PaywallSheet, UpgradePrompt
│
├── hoc/                        the cross-cutting concerns
├── lib/
│   ├── api/                    client, endpoints, types, errors, tokenStore, queryKeys
│   ├── hooks/                  one per domain
│   ├── stores/                 authStore, uiStore
│   ├── ws/                     the STOMP singleton
│   └── utils/                  cn, formatting
└── providers/                  QueryProvider, SessionProvider, AppProviders
```

The dependency direction is strict: **pages use hooks, hooks use endpoints, endpoints use the
client.** No component builds a URL. A backend route change is a one-file edit.

---

## The HOC layer

Five HOCs, each owning one concern that would otherwise be copy-pasted into every page.

| HOC | Concern |
| --- | --- |
| `withAuth` | Requires a session, and optionally a finished onboarding |
| `withGuest` | The inverse, for login and register |
| `withEntitlement` | Requires a paid feature, renders the upsell otherwise |
| `withErrorBoundary` | Contains a render crash to one screen |
| `withQueryState` | Removes the loading / error / empty triangle from list components |

### Why HOCs and not hooks

For `withAuth` specifically: a guard has to decide whether the wrapped component renders **at
all**. A hook runs *inside* the component, so the protected UI would mount and fire its
queries before the redirect - a visible flash of private chrome and a burst of 401s. Wrapping
lets the guard return a loader instead of ever mounting the page.

`withQueryState` is a different argument: it makes the wrapped component a pure function of
loaded data. It can assume `data` exists, which removes the three-branch ternary that
otherwise appears at the top of every list.

### Composition order

```tsx
export default compose(withErrorBoundary, withAuth)(MatchesPage);
```

Outermost first. The order is not arbitrary:

- The **boundary wraps the guard**, so a crash while resolving auth is still caught.
- The **entitlement check sits inside the auth check**, because you need a signed-in user
  before you can ask what they are entitled to.

Onboarding is the one exception and says so in the code:

```tsx
// Onboarding is the one page that must NOT require a completed onboarding,
// or the guard would redirect it to itself.
const Guarded = withAuth(OnboardingPage, { requireOnboarding: false });
export default withErrorBoundary(Guarded);
```

---

## State

Three kinds, three tools:

| Kind | Tool | Example |
| --- | --- | --- |
| Server state | TanStack Query | profiles, matches, conversations |
| Session | Zustand (`authStore`) | who is signed in |
| Ephemeral UI | Zustand (`uiStore`) | toasts, the paywall sheet, the filter sheet |
| Live thread state | local `useState` in `useConversation` | the message list |

Query keys are hierarchical in `queryKeys.ts`, so invalidating `queryKeys.chat.all` clears
every conversation and thread in one call - which is exactly what an "unmatched" event needs.

### The HTTP client

`lib/api/client.ts` is the only place that calls `fetch`. It:

- unwraps the `ApiResponse` envelope and throws a typed `ApiError`;
- attaches the bearer token;
- on a 401, refreshes once and replays the original request;
- de-duplicates concurrent refreshes, so ten simultaneous 401s trigger one refresh.

Retries are configured to never fire on a 401 or a paywall. Retrying a 401 hides the
sign-out; retrying `QUOTA_EXCEEDED` hammers a limit the user has already hit.

### Tokens

Kept in memory and mirrored to `localStorage` so a refresh does not sign the user out. A
deliberate trade-off: an httpOnly cookie is stronger against XSS, but this client talks to a
stateless API across an origin boundary, and memory-first keeps the token out of every
request the browser makes on its own. Access tokens live 15 minutes, which bounds the damage.

---

## Real-time

One STOMP connection for the whole app (`lib/ws/socket.ts`), a module-level singleton.
A socket per screen would mean a reconnect storm on every tab change.

Subscriptions made before the connection is live are queued and replayed on connect, which
removes the race between a component mounting and the handshake finishing.

`SessionProvider` ties the socket to the session: connect on sign-in, disconnect on sign-out.

---

## Paywalls

Any mutation can pass its error to `usePaywall().handleError`. It recognises
`QUOTA_EXCEEDED` and `PREMIUM_REQUIRED`, reads the server's context - the real limit, the
real reset time, the real tier - and opens the sheet. Anything else becomes a toast.

The result is that the paywall copy is specific ("resets in 4 hours", "available on Premium")
without the client knowing anything about pricing.

---

## Design

One accent colour, three ink levels, two surfaces, defined as CSS variables in
`globals.css` and swapped wholesale for dark mode. No component writes a `dark:` variant for
colour.

- Focus rings are never removed.
- Every list has a skeleton, an empty state and an error state, all through `withQueryState`.
- `prefers-reduced-motion` disables animation globally.
- The bottom nav clears the iOS home indicator with `env(safe-area-inset-bottom)`.
- Touch targets are at least 44px.

---

## Commands

```bash
npm run dev         # development server
npm run build       # production build
npm run typecheck   # tsc --noEmit
npm run lint        # next lint
```

`tsconfig.json` runs with `strict`, `noUncheckedIndexedAccess`, `noUnusedLocals` and
`noUnusedParameters`. The first of those is the one that catches real bugs: `photos[0]` is
`Photo | undefined`, and the compiler makes you say what happens when a profile has none.
