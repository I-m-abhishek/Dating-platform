package com.dating.platform.match.engine;

import com.dating.platform.profile.entity.Interest;
import com.dating.platform.profile.entity.Profile;
import com.dating.platform.profile.entity.Quality;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.entity.enums.ChildrenPreference;
import com.dating.platform.user.entity.enums.LifestyleChoice;
import com.dating.platform.user.entity.enums.RelationshipIntent;
import com.dating.platform.util.DateUtils;
import com.dating.platform.util.GeoUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scores how well two people fit, on 0..1.
 *
 * <p>Seven dimensions, each normalised to 0..1 and combined with {@link ScoringWeights}:
 *
 * <ol>
 *   <li><b>interests</b> - Jaccard overlap of interest tags.</li>
 *   <li><b>qualities</b> - affinity aware overlap: traits with a positive affinity weight
 *       reward sameness, negative ones reward complementarity across a dimension.</li>
 *   <li><b>intent</b> - how compatible their stated relationship goals are.</li>
 *   <li><b>lifestyle</b> - drinking, smoking and children answers, where a mismatch on
 *       children is weighted hardest because it is the most common deal breaker.</li>
 *   <li><b>age</b> - how well each person sits inside the other's stated age range.</li>
 *   <li><b>distance</b> - decays towards the stricter of the two distance preferences.</li>
 *   <li><b>effort</b> - profile completeness, a proxy for "will actually reply".</li>
 * </ol>
 *
 * <p>This class is pure and stateless: no repositories, no clock beyond ages. That makes
 * it directly unit testable, which matters because it decides who meets whom.
 */
@Component
public class CompatibilityScorer {

    private static final ScoringWeights WEIGHTS = ScoringWeights.defaults();

    public CompatibilityScore score(User a, Profile aProfile, User b, Profile bProfile) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        List<String> highlights = new ArrayList<>();

        double interests = interestOverlap(aProfile, bProfile);
        double qualities = qualityAffinity(aProfile, bProfile);
        double intent = intentAlignment(aProfile.getRelationshipIntent(), bProfile.getRelationshipIntent());
        double lifestyle = lifestyleAlignment(aProfile, bProfile);
        double age = ageFit(a, b);
        double distance = distanceFit(a, b);
        double effort = (aProfile.getCompleteness() + bProfile.getCompleteness()) / 2d;

        breakdown.put("interests", interests);
        breakdown.put("qualities", qualities);
        breakdown.put("intent", intent);
        breakdown.put("lifestyle", lifestyle);
        breakdown.put("age", age);
        breakdown.put("distance", distance);
        breakdown.put("effort", effort);

        double total = WEIGHTS.interests() * interests
                + WEIGHTS.qualities() * qualities
                + WEIGHTS.intent() * intent
                + WEIGHTS.lifestyle() * lifestyle
                + WEIGHTS.age() * age
                + WEIGHTS.distance() * distance
                + WEIGHTS.effort() * effort;

        List<String> shared = sharedInterestLabels(aProfile, bProfile);
        if (shared.size() >= 2) {
            highlights.add("You both like " + String.join(" and ", shared.subList(0, 2)));
        } else if (shared.size() == 1) {
            highlights.add("You both like " + shared.get(0));
        }
        if (intent >= 0.9 && bProfile.getRelationshipIntent() != null) {
            highlights.add("Looking for the same thing");
        }
        if (distance >= 0.8) {
            highlights.add("Close by");
        }

        return new CompatibilityScore(round(total), breakdown, highlights);
    }

    public List<String> sharedInterestLabels(Profile a, Profile b) {
        Set<String> bSlugs = slugs(b.getInterests());
        return a.getInterests() == null ? List.of() : a.getInterests().stream()
                .filter(i -> bSlugs.contains(i.getSlug()))
                .map(Interest::getLabel)
                .sorted()
                .toList();
    }

    // ---- dimensions ----------------------------------------------------

    private double interestOverlap(Profile a, Profile b) {
        Set<String> sa = slugs(a.getInterests());
        Set<String> sb = slugs(b.getInterests());
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0.25; // unknown, not incompatible
        }
        long intersection = sa.stream().filter(sb::contains).count();
        long union = sa.size() + sb.size() - intersection;
        double jaccard = union == 0 ? 0 : (double) intersection / union;
        // Jaccard tops out low for large tag sets, so rescale into a usable range.
        return Math.min(1d, jaccard * 2.5);
    }

    private double qualityAffinity(Profile a, Profile b) {
        Set<Quality> qa = a.getQualities();
        Set<Quality> qb = b.getQualities();
        if (qa == null || qb == null || qa.isEmpty() || qb.isEmpty()) {
            return 0.25;
        }
        Map<String, Quality> bByDimension = qb.stream()
                .collect(Collectors.toMap(Quality::getDimension, q -> q, (x, y) -> x));

        double score = 0d;
        int considered = 0;
        for (Quality quality : qa) {
            Quality counterpart = bByDimension.get(quality.getDimension());
            if (counterpart == null) {
                continue;
            }
            considered++;
            boolean same = quality.getSlug().equals(counterpart.getSlug());
            double weight = (quality.getAffinityWeight() + counterpart.getAffinityWeight()) / 2d;
            // weight > 0 means "same is good"; weight < 0 means "opposites attract"
            score += same ? normalise(weight) : normalise(-weight);
        }
        return considered == 0 ? 0.25 : Math.min(1d, score / considered);
    }

    private double intentAlignment(RelationshipIntent a, RelationshipIntent b) {
        if (a == null || b == null) {
            return 0.5;
        }
        if (a == b) {
            return 1d;
        }
        boolean onlyOneWantsFriends =
                (a == RelationshipIntent.NEW_FRIENDS) != (b == RelationshipIntent.NEW_FRIENDS);
        if (onlyOneWantsFriends) {
            return 0.1;
        }
        int distance = Math.abs(intentRank(a) - intentRank(b));
        return switch (distance) {
            case 1 -> 0.8;
            case 2 -> 0.5;
            case 3 -> 0.25;
            default -> 0.1;
        };
    }

    private int intentRank(RelationshipIntent intent) {
        return switch (intent) {
            case LONG_TERM -> 0;
            case LONG_TERM_OPEN_TO_SHORT -> 1;
            case FIGURING_IT_OUT -> 2;
            case SHORT_TERM_OPEN_TO_LONG -> 3;
            case SHORT_TERM -> 4;
            case NEW_FRIENDS -> 5;
        };
    }

    private double lifestyleAlignment(Profile a, Profile b) {
        double childrenScore = childrenAlignment(a.getChildren(), b.getChildren());
        double drinkScore = lifestyleChoiceAlignment(a.getDrinking(), b.getDrinking());
        double smokeScore = lifestyleChoiceAlignment(a.getSmoking(), b.getSmoking());
        // Children is the most common deal breaker, so it carries half the dimension.
        return 0.5 * childrenScore + 0.25 * drinkScore + 0.25 * smokeScore;
    }

    private double childrenAlignment(ChildrenPreference a, ChildrenPreference b) {
        if (a == null || b == null
                || a == ChildrenPreference.PREFER_NOT_TO_SAY || b == ChildrenPreference.PREFER_NOT_TO_SAY) {
            return 0.5;
        }
        if (a == b) {
            return 1d;
        }
        if (a == ChildrenPreference.NOT_SURE || b == ChildrenPreference.NOT_SURE
                || a == ChildrenPreference.OPEN_TO_CHILDREN || b == ChildrenPreference.OPEN_TO_CHILDREN) {
            return 0.65;
        }
        return wantsChildren(a) == wantsChildren(b) ? 0.85 : 0.15;
    }

    private boolean wantsChildren(ChildrenPreference preference) {
        return preference == ChildrenPreference.WANT_SOMEDAY
                || preference == ChildrenPreference.HAVE_AND_WANT_MORE
                || preference == ChildrenPreference.OPEN_TO_CHILDREN;
    }

    private double lifestyleChoiceAlignment(LifestyleChoice a, LifestyleChoice b) {
        if (a == null || b == null
                || a == LifestyleChoice.PREFER_NOT_TO_SAY || b == LifestyleChoice.PREFER_NOT_TO_SAY) {
            return 0.5;
        }
        if (a == b) {
            return 1d;
        }
        boolean oneIsSometimes = a == LifestyleChoice.SOMETIMES || b == LifestyleChoice.SOMETIMES;
        return oneIsSometimes ? 0.7 : 0.25;
    }

    private double ageFit(User a, User b) {
        int ageA = DateUtils.ageOf(a.getDateOfBirth());
        int ageB = DateUtils.ageOf(b.getDateOfBirth());
        return (insideRange(ageB, a.getPreferredMinAge(), a.getPreferredMaxAge())
                + insideRange(ageA, b.getPreferredMinAge(), b.getPreferredMaxAge())) / 2d;
    }

    private double insideRange(int age, int min, int max) {
        if (age >= min && age <= max) {
            return 1d;
        }
        int overshoot = age < min ? min - age : age - max;
        return Math.max(0d, 1d - (overshoot / 8d)); // fades out over 8 years
    }

    private double distanceFit(User a, User b) {
        if (a.getLatitude() == null || b.getLatitude() == null) {
            return 0.3;
        }
        if (a.isGlobalMode() || b.isGlobalMode()) {
            return 0.7;
        }
        double km = GeoUtils.distanceKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        int tolerance = Math.min(a.getPreferredMaxDistanceKm(), b.getPreferredMaxDistanceKm());
        if (tolerance <= 0) {
            return 0.3;
        }
        return Math.max(0d, 1d - (km / (tolerance * 1.25)));
    }

    // ---- helpers -------------------------------------------------------

    private Set<String> slugs(Set<Interest> interests) {
        return interests == null ? Set.of()
                : interests.stream().map(Interest::getSlug).collect(Collectors.toSet());
    }

    /** Maps an affinity weight in [-1, 1] onto a 0..1 score. */
    private double normalise(double weight) {
        return Math.max(0d, Math.min(1d, (weight + 1d) / 2d));
    }

    private double round(double value) {
        return Math.round(Math.max(0d, Math.min(1d, value)) * 1000d) / 1000d;
    }
}
