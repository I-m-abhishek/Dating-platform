package com.dating.platform.match.service;

import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.config.AppProperties;
import com.dating.platform.discovery.dto.FeedFilterRequest;
import com.dating.platform.discovery.service.CandidateFinder;
import com.dating.platform.match.dto.AutoMatchResponse;
import com.dating.platform.match.dto.AutoMatchRunResponse;
import com.dating.platform.match.engine.CompatibilityScore;
import com.dating.platform.match.engine.CompatibilityScorer;
import com.dating.platform.match.entity.AutoMatchRun;
import com.dating.platform.match.entity.AutoMatchRun.Cadence;
import com.dating.platform.match.entity.AutoMatchRun.Outcome;
import com.dating.platform.match.entity.Match;
import com.dating.platform.match.entity.MatchSource;
import com.dating.platform.match.repository.AutoMatchRunRepository;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.repository.ProfileRepository;
import com.dating.platform.quota.entity.QuotaFeature;
import com.dating.platform.quota.service.QuotaService;
import com.dating.platform.subscription.dto.Entitlements;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.service.EntitlementService;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.UserStatus;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Guarantees everybody a match on a schedule: once a week for free accounts, once a day
 * with Premium.
 *
 * <p><b>Design notes.</b>
 * <ul>
 *   <li><b>Idempotency.</b> One {@link AutoMatchRun} row per (user, cadence, period) with a
 *       unique constraint. A scheduler retry, a manual trigger and a redeploy mid-run all
 *       converge on the same outcome instead of handing someone two matches.</li>
 *   <li><b>Fairness.</b> A user already auto-matched this period is excluded from everyone
 *       else's candidate pool, so the most attractive profiles are not consumed by whoever
 *       the scheduler happened to process first.</li>
 *   <li><b>Reciprocity.</b> Both sides get a run row pointing at the same match, so the
 *       partner's weekly slot is spent too - the pairing is mutual, not a one-way gift.</li>
 *   <li><b>Honest failure.</b> Running out of candidates records {@code NO_CANDIDATE} rather
 *       than lowering the bar. A bad match is worse than no match.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoMatchService {

    private static final double MIN_COMPLETENESS = 0.35;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final AutoMatchRunRepository autoMatchRunRepository;
    private final CandidateFinder candidateFinder;
    private final CompatibilityScorer compatibilityScorer;
    private final MatchService matchService;
    private final QuotaService quotaService;
    private final EntitlementService entitlementService;
    private final AppProperties appProperties;

    /**
     * Runs an auto-match on demand (the "Find me a match now" button).
     * Chooses the cadence the user is entitled to.
     */
    @Transactional
    public AutoMatchResponse runOnDemand(UUID userId) {
        Entitlements entitlements = entitlementService.entitlementsOf(userId);
        boolean daily = entitlements.has(Feature.DAILY_AUTO_MATCH);
        Cadence cadence = daily ? Cadence.DAILY : Cadence.WEEKLY;

        // The quota gives the paywall its message; the run row gives idempotency.
        quotaService.consume(userId,
                daily ? QuotaFeature.AUTO_MATCH_DAILY : QuotaFeature.AUTO_MATCH_WEEKLY,
                daily ? entitlements.autoMatchPerDay() : entitlements.autoMatchPerWeek(),
                daily ? "You have had your match for today" : "Upgrade to Premium for a new match every day");

        return runFor(userId, cadence);
    }

    /**
     * Executes one auto-match for one user in its own transaction, so a single bad user
     * cannot abort a scheduled batch of thousands.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutoMatchResponse runFor(UUID userId, Cadence cadence) {
        String periodKey = periodKeyFor(cadence);

        Optional<AutoMatchRun> existing =
                autoMatchRunRepository.findByUserIdAndCadenceAndPeriodKey(userId, cadence, periodKey);
        if (existing.isPresent()) {
            return toResponse(userId, existing.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Profile profile = profileRepository.findByUserId(userId).orElse(null);

        if (!isEligible(user, profile)) {
            return record(userId, cadence, periodKey, Outcome.SKIPPED_INELIGIBLE, null, null, null, 0,
                    "Finish your profile to join the next auto-match");
        }

        List<UUID> excluded = new ArrayList<>(matchService.matchedCounterpartIds(userId));
        excluded.add(userId);
        excluded.addAll(alreadyAutoMatchedThisPeriod(cadence, periodKey));

        List<UUID> candidateIds = candidateFinder.findCandidateIds(
                user, FeedFilterRequest.empty(), excluded.stream().distinct().toList(),
                appProperties.matching().candidatePoolSize());

        if (candidateIds.isEmpty()) {
            return record(userId, cadence, periodKey, Outcome.NO_CANDIDATE, null, null, null, 0,
                    "No one new nearby this time - we will try again next run");
        }

        Optional<Scored> best = bestCandidate(user, profile, candidateIds);
        if (best.isEmpty()) {
            return record(userId, cadence, periodKey, Outcome.BELOW_THRESHOLD, null, null, null,
                    candidateIds.size(), "Nobody cleared the compatibility bar this time");
        }

        Scored winner = best.get();
        Optional<Match> match = matchService.createMatch(userId, winner.user().getId(),
                cadence == Cadence.DAILY ? MatchSource.AUTO_MATCH_DAILY : MatchSource.AUTO_MATCH_WEEKLY,
                winner.score().total(), winner.score().highlights());

        if (match.isEmpty()) {
            return record(userId, cadence, periodKey, Outcome.FAILED, null, null,
                    winner.score().total(), candidateIds.size(), "Could not create the match");
        }

        // Spend the partner's slot for this period too - the pairing is mutual.
        recordQuietly(winner.user().getId(), cadence, periodKey, Outcome.MATCHED, userId,
                match.get().getId(), winner.score().total(), candidateIds.size(), null);

        return record(userId, cadence, periodKey, Outcome.MATCHED, winner.user().getId(),
                match.get().getId(), winner.score().total(), candidateIds.size(), null);
    }

    @Transactional(readOnly = true)
    public List<AutoMatchRunResponse> history(UUID userId) {
        return autoMatchRunRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AutoMatchRunResponse::from)
                .toList();
    }

    /** Everyone the scheduler should consider this period. */
    @Transactional(readOnly = true)
    public List<UUID> eligibleUserIds(Cadence cadence) {
        List<User> users = userRepository.findAutoMatchEligible();
        if (cadence == Cadence.WEEKLY) {
            return users.stream().map(User::getId).toList();
        }
        // Daily runs are a Premium entitlement, so filter down to those who hold it.
        return users.stream()
                .map(User::getId)
                .filter(id -> entitlementService.has(id, Feature.DAILY_AUTO_MATCH))
                .toList();
    }

    // ---- selection -----------------------------------------------------

    private Optional<Scored> bestCandidate(User user, Profile profile, List<UUID> candidateIds) {
        Map<UUID, User> users = userRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Profile> profiles = profileRepository.findAllByUserIdIn(candidateIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        double threshold = appProperties.matching().minCompatibilityScore();

        return candidateIds.stream()
                .map(id -> {
                    User candidate = users.get(id);
                    Profile candidateProfile = profiles.get(id);
                    if (candidate == null || candidateProfile == null) {
                        return null;
                    }
                    CompatibilityScore score =
                            compatibilityScorer.score(user, profile, candidate, candidateProfile);
                    return new Scored(candidate, score);
                })
                .filter(java.util.Objects::nonNull)
                .filter(s -> s.score().total() >= threshold)
                .max(Comparator.comparingDouble(s -> s.score().total()));
    }

    private boolean isEligible(User user, Profile profile) {
        if (user.getStatus() != UserStatus.ACTIVE || user.getOnboardingCompletedAt() == null) {
            return false;
        }
        if (user.getLatitude() == null || user.getLongitude() == null) {
            return false;
        }
        // A near-empty profile produces a match nobody can evaluate, so it waits a period.
        return profile != null && profile.getCompleteness() >= MIN_COMPLETENESS;
    }

    private List<UUID> alreadyAutoMatchedThisPeriod(Cadence cadence, String periodKey) {
        return autoMatchRunRepository.findUserIdsWithOutcome(cadence, periodKey, Outcome.MATCHED);
    }

    // ---- bookkeeping ---------------------------------------------------

    private AutoMatchResponse record(UUID userId, Cadence cadence, String periodKey, Outcome outcome,
                                     UUID matchedUserId, UUID matchId, Double score,
                                     int candidates, String note) {
        AutoMatchRun run = recordQuietly(userId, cadence, periodKey, outcome, matchedUserId, matchId,
                score, candidates, note);
        return toResponse(userId, run);
    }

    /**
     * Writes the audit row. A unique-constraint violation means a concurrent run won the
     * race; we return that run instead of failing, which is what makes retries safe.
     */
    private AutoMatchRun recordQuietly(UUID userId, Cadence cadence, String periodKey, Outcome outcome,
                                       UUID matchedUserId, UUID matchId, Double score,
                                       int candidates, String note) {
        try {
            return autoMatchRunRepository.saveAndFlush(AutoMatchRun.builder()
                    .userId(userId)
                    .cadence(cadence)
                    .periodKey(periodKey)
                    .outcome(outcome)
                    .matchedUserId(matchedUserId)
                    .matchId(matchId)
                    .score(score)
                    .candidatesConsidered(candidates)
                    .note(note)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("Auto-match run already recorded for {} {} {}", userId, cadence, periodKey);
            return autoMatchRunRepository.findByUserIdAndCadenceAndPeriodKey(userId, cadence, periodKey)
                    .orElseThrow(() -> e);
        }
    }

    private AutoMatchResponse toResponse(UUID userId, AutoMatchRun run) {
        return new AutoMatchResponse(
                run.getOutcome(),
                run.getCadence(),
                run.getPeriodKey(),
                run.getMatchId() == null ? null : matchService.getMatch(userId, run.getMatchId()),
                run.getScore(),
                run.getCandidatesConsidered(),
                run.getCadence() == Cadence.DAILY ? DateUtils.nextDailyReset() : DateUtils.nextWeeklyReset(),
                run.getNote() != null ? run.getNote() : messageFor(run.getOutcome()));
    }

    private String messageFor(Outcome outcome) {
        return switch (outcome) {
            case MATCHED -> "Your match is ready";
            case NO_CANDIDATE -> "No one new nearby this time";
            case BELOW_THRESHOLD -> "Nobody cleared the compatibility bar this time";
            case SKIPPED_INELIGIBLE -> "Finish your profile to join the next auto-match";
            case FAILED -> "Something went wrong - we will retry";
        };
    }

    private String periodKeyFor(Cadence cadence) {
        return cadence == Cadence.WEEKLY
                ? DateUtils.isoWeekKey(DateUtils.today())
                : DateUtils.today().toString();
    }

    private record Scored(User user, CompatibilityScore score) {
    }
}
