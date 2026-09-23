# Matching

Three algorithms, all in `com.dating.platform.match.engine` and
`com.dating.platform.standout.service`. All three are pure and unit tested, because they
decide who meets whom.

---

## 1. Compatibility scoring

`CompatibilityScorer` produces a number in `0..1` for a pair of people. Seven dimensions,
each normalised to `0..1`, combined with the weights in `ScoringWeights`:

| Dimension | Weight | How it is computed |
| --- | --- | --- |
| Interests | 0.24 | Jaccard overlap of interest slugs, rescaled by 2.5 |
| Qualities | 0.20 | Affinity-aware comparison within shared dimensions |
| Intent | 0.16 | Distance between stated relationship goals |
| Lifestyle | 0.14 | Children (half), drinking, smoking |
| Age | 0.10 | How well each sits inside the other's stated range |
| Distance | 0.10 | Decay towards the stricter of the two tolerances |
| Effort | 0.06 | Mean profile completeness |

### Why each one looks the way it does

**Interests, rescaled Jaccard.** Raw Jaccard tops out low for realistic tag sets: two people
with twelve interests each and four in common score 0.2, which is actually a strong overlap.
Multiplying by 2.5 and clamping puts the realistic range where the weights expect it.
Unknown (either side has no interests) returns 0.25, not 0 - an empty profile is
uninformative, not incompatible.

**Qualities, affinity weights.** Some traits pair best with themselves and some with their
opposite, so every quality carries an `affinity_weight` in `[-1, 1]` and belongs to a
`dimension`. Two people are compared only within dimensions they both answered. `+1.0`
("Loyal", "Curious") rewards sameness. `-0.4` ("Spontaneous" against "Organised") rewards
complementarity. This is the reason qualities are reference data and not free text: set
overlap over free text is meaningless.

**Intent, ranked with one hard rule.** Goals are ranked `LONG_TERM` through `NEW_FRIENDS` and
scored by distance. The exception: if exactly one person picked `NEW_FRIENDS`, the score
collapses to 0.1 regardless of rank distance. Someone looking for friends and someone looking
for a relationship is not a near miss, it is the wrong match.

**Lifestyle, children carries half.** Children is the most common deal breaker in real dating
data, so it is weighted at 0.5 of the dimension against 0.25 each for drinking and smoking.
Two people who disagree on children score 0.15 there; `NOT_SURE` or `OPEN_TO_CHILDREN` on
either side softens it to 0.65 rather than punishing openness.

**Age, mutual.** Both directions are checked: how well B sits in A's range, and A in B's.
Outside the range the score fades over eight years rather than dropping to zero, because a
one-year miss is not a rejection.

**Distance, relative rather than absolute.** Decays towards the stricter of the two people's
tolerances. Someone willing to travel 200 km still will not match someone who set 10 km.

**Effort, profile completeness.** A proxy for "will actually reply". Small weight, but it is
the difference between two otherwise identical candidates.

### Properties

- **Symmetric.** `score(a, b)` equals `score(b, a)`. Both people see the same number, which
  matters the moment it is displayed.
- **Total.** Every branch has a defined value for missing data; no combination of nulls
  throws.
- **Explainable.** Every score carries a `breakdown` map and human `highlights`
  ("You both like hiking and coffee"), which is what the match card renders.

---

## 2. Auto-match

`AutoMatchService`. Free accounts get one match a week; Premium gets one a day.

### The run

1. **Idempotency check.** Look for an `auto_match_runs` row for this (user, cadence, period).
   If it exists, return it. Nothing else happens.
2. **Eligibility.** Active, onboarded, located, and profile completeness at least 0.35. A
   near-empty profile is skipped with `SKIPPED_INELIGIBLE` and waits a period - sending
   someone a blank profile as their guaranteed match of the week is worse than sending
   nothing.
3. **Candidate pool.** `CandidateFinder`, the same query the home feed uses. Excluded:
   existing matches, blocked pairs, and anyone already auto-matched this period.
4. **Score and pick.** Every candidate scored; the highest above
   `app.matching.min-compatibility-score` (0.35) wins.
5. **Create.** A `Match` with source `AUTO_MATCH_WEEKLY` or `AUTO_MATCH_DAILY`, its
   conversation, and notifications to both people.
6. **Record both sides.** An `auto_match_runs` row for the partner too, pointing at the same
   match.

### The three decisions that matter

**Idempotency is a database constraint, not a flag.** The unique index on
`(user_id, cadence, period_key)` means a scheduler retry, a manual "find me a match" tap and
a redeploy mid-run all converge. `recordQuietly` catches the constraint violation and returns
the row the winner wrote.

**Fairness is an exclusion, not a tiebreak.** Anyone already auto-matched this period is
removed from everyone else's candidate pool. Without it the most attractive profiles are
consumed by whoever the scheduler processed first, and the last user in the batch gets the
leftovers. This is why `findUserIdsWithOutcome` exists.

**Reciprocity costs the partner their slot.** Both sides get a run row. The pairing is
mutual, so it spends both allowances - otherwise a popular profile would be handed out as
everyone's weekly match.

### Failure is a result, not an error

| Outcome | Meaning | HTTP |
| --- | --- | --- |
| `MATCHED` | A match was created | 200 |
| `NO_CANDIDATE` | Nobody eligible nearby | 200 |
| `BELOW_THRESHOLD` | Candidates existed, none good enough | 200 |
| `SKIPPED_INELIGIBLE` | Profile too thin | 200 |
| `FAILED` | Something broke | 200, logged |

Only a spent allowance returns an error (`402 QUOTA_EXCEEDED`), because that is the one case
where the right response is a paywall. The rest are honest answers and the UI says so.

### Scheduling

```properties
# Monday 09:00 UTC
app.matching.weekly-auto-match-cron=0 0 9 * * MON
# every day 09:00 UTC
app.matching.daily-auto-match-cron=0 0 9 * * *
```

`AutoMatchScheduler` picks the eligible users and calls `runFor` per user. Each call is its
own transaction (`REQUIRES_NEW`, which applies because the call crosses a bean boundary), so
one failure cannot roll back the batch.

---

## 3. Standouts

`PopularityScorer` plus `StandoutRefreshService`.

The naive version - rank by like count - produces a leaderboard of the same ten people
forever. Three corrections:

| Signal | Weight | Why |
| --- | --- | --- |
| Popularity | 0.45 | `log1p(likes + 2 x superLikes) / log1p(120)`. The 400th like is worth far less than the 4th, so a runaway favourite cannot own the shelf. Capped at 45% so the other signals can outweigh it. |
| Completeness | 0.20 | A full profile is worth looking at. |
| Recent activity | 0.20 | Linear decay over seven days. Nobody wants to be shown a dormant account. |
| Newcomer boost | 0.15 | Decays over 14 days. Lets someone who joined yesterday appear with zero likes - without it the shelf is closed to new users, and new users are exactly who needs visibility. |

Each tile carries a reason - `POPULAR`, `NEW`, `ACTIVE`, `RISING` - rendered as a badge. A
ranked shelf with no explanation reads as arbitrary.

### Read path

Rankings are recomputed every six hours into `standout_snapshots` under a new `cycle_key`,
then the old cycle is dropped. Reads are one indexed range scan plus bulk hydration.

Personalisation still happens per request: blocked users, existing matches and anyone already
swiped are filtered out, mutual gender preference is checked, and each tile carries the
viewer's own compatibility score. A global leaderboard with no personal filter would show you
your own ex.

---

## Tuning

Everything is configuration, not code (`application.properties`):

```properties
# hard cap on rows scored per run
app.matching.candidate-pool-size=400
app.matching.max-distance-km-default=80
# the auto-match bar
app.matching.min-compatibility-score=0.35
```

Weights live in `ScoringWeights.defaults()` as a record, so they can be A/B tested or
personalised without touching the scorer.
