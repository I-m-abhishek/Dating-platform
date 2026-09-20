# Tiers, features and quotas

Two systems, and the distinction matters:

- **Features** are boolean unlocks. "Can this account see who likes it?" Modelled by
  `Feature`, resolved by `EntitlementService`.
- **Quotas** are metered allowances. "How many comments today?" Modelled by `QuotaFeature`,
  enforced by `QuotaService` against a persisted counter.

---

## Tiers

| | Free | Plus | Premium |
| --- | --- | --- | --- |
| Price | — | $9.99/mo | $19.99/mo |
| Photo comments per day | 5 | 25 | 100 |
| Likes per day | 20 | 100 | unlimited |
| Auto-match | 1 a week | 3 a week | 1 a **day** |
| Rewinds per day | 0 | 5 | unlimited |
| See who likes you | — | yes | yes |
| Advanced filters | — | yes | yes |
| Read receipts | — | yes | yes |
| Global mode | — | — | yes |
| Incognito | — | — | yes |

`-1` in the API means unlimited.

---

## Where a limit lives

One place: `application.yml`.

```yaml
app:
  quota:
    free:
      photo-comments-per-day: 5
      likes-per-day: 20
      auto-match-per-week: 1
      rewinds-per-day: 0
    plus:
      photo-comments-per-day: 25
      likes-per-day: 100
      auto-match-per-week: 3
      rewinds-per-day: 5
    premium:
      photo-comments-per-day: 100
      likes-per-day: -1
      auto-match-per-day: 1
      rewinds-per-day: -1
```

**To change the free comment allowance from 5 to 3, edit that one line.** The backend reads
it through `AppProperties`, and the frontend reads the resulting number from
`GET /subscriptions/me/entitlements`. Nothing in the client hardcodes a limit - that is the
whole reason the entitlements endpoint exists.

---

## Adding a paywall

Three steps, and there is no fourth:

1. Add a constant to `Feature` with the tier that unlocks it.
2. Call `entitlementService.require(userId, Feature.X)` in the service.
3. Wrap the screen in `withEntitlement(Component, 'X')` on the frontend, or handle the
   `PREMIUM_REQUIRED` error with `usePaywall`.

Never write `if (tier == PREMIUM)` inline. The whole point of the `Feature` enum is that
"what does Plus include?" has exactly one answer, in one file.

---

## How a quota is spent

```java
Entitlements entitlements = entitlementService.entitlementsOf(userId);
quotaService.consume(userId, QuotaFeature.PHOTO_COMMENT,
        entitlements.photoCommentsPerDay(),
        "Upgrade to leave more comments every day");
```

Consumption happens **before** the write and inside the same transaction. Two consequences,
both intentional:

- If the write fails, the transaction rolls back and the token comes back automatically.
- Two simultaneous requests cannot spend the same last token, because consumption is a single
  conditional `UPDATE ... WHERE used < :limit`. Zero rows updated means refused.

On refusal, `QuotaExceededException` carries the feature, the limit, the reset time and an
upgrade hint. The frontend's `usePaywall` turns exactly that into the sheet - which is why
the copy can say "resets in 4 hours" instead of something vague.

---

## Enforcement is server side

The Likes You tab is the clearest case. For an account without `SEE_WHO_LIKES_YOU`,
`LikeService.blurredRow()` builds a placeholder with **no user id, no name, no photo URL and
no note**. The CSS blur in the client is decoration over an empty object.

A paywall that can be lifted by opening dev tools is not a paywall.

---

## Payments

Deliberately stubbed. `SubscriptionService.subscribe` records the token verbatim and grants
the plan immediately.

To make it real:

1. Verify the token with the provider before creating the `Subscription`.
2. Add a webhook that sets `status` to `ACTIVE`, `PAST_DUE`, `CANCELLED` or `EXPIRED` and
   calls `entitlementService.evict(userId)`.
3. Delete the scheduled `expireLapsedSubscriptions` sweep, or keep it as a safety net for
   webhooks that never arrive.

Nothing else changes, because nothing else reads payment state - everything downstream asks
`EntitlementService`.
