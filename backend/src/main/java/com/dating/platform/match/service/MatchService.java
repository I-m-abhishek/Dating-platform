package com.dating.platform.match.service;

import com.dating.platform.chat.entity.Conversation;
import com.dating.platform.chat.repository.ConversationRepository;
import com.dating.platform.common.exception.ForbiddenException;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.match.dto.MatchResponse;
import com.dating.platform.match.entity.Match;
import com.dating.platform.match.entity.MatchSource;
import com.dating.platform.match.entity.MatchStatus;
import com.dating.platform.match.repository.MatchRepository;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.service.NotificationService;
import com.dating.platform.profile.service.UserSummaryService;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.user.dto.UserSummaryResponse;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates and reads matches, and owns the conversation that every match gets.
 *
 * <p>A match is created in exactly two ways: a reciprocated like (from
 * {@code LikeService}) or the auto-match engine. Both funnel through
 * {@link #createMatch} so the conversation, notifications and audit trail can never
 * diverge between the two paths.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final UserSummaryService userSummaryService;
    private final NotificationService notificationService;
    private final BlockService blockService;

    /**
     * Creates the match and its conversation, or returns the existing one.
     *
     * @return the match, or empty when the pair is blocked
     */
    @Transactional
    public Optional<Match> createMatch(UUID userOne, UUID userTwo, MatchSource source,
                                       Double score, List<String> highlights) {
        if (userOne.equals(userTwo)) {
            return Optional.empty();
        }
        if (blockService.isBlockedEitherWay(userOne, userTwo)) {
            log.debug("Refusing to match blocked pair {} / {}", userOne, userTwo);
            return Optional.empty();
        }

        Optional<Match> existing = matchRepository.findByPair(userOne, userTwo);
        if (existing.isPresent()) {
            return existing;
        }

        Match match = Match.between(userOne, userTwo);
        match.setSource(source);
        match.setStatus(MatchStatus.ACTIVE);
        match.setCompatibilityScore(score);
        match.setHighlights(highlights == null || highlights.isEmpty() ? null : String.join("|", highlights));
        match.setLastInteractionAt(Instant.now());

        /*
         * The simultaneous mutual like.
         *
         * A and B like each other in the same instant on two threads. Both ran findByPair
         * above and both saw nothing, because under READ COMMITTED neither can see the
         * other's uncommitted row. Both now insert.
         *
         * uk_matches_pair decides it: the canonical (userA, userB) ordering means both
         * threads are inserting the SAME row, so exactly one wins and the other gets a
         * duplicate-key violation. Catching it and reading the winner's row is what turns a
         * 500 for one of the two users into both of them seeing the same match.
         *
         * flush() forces the INSERT here rather than at commit, so the violation surfaces
         * inside this try instead of escaping the method as a commit-time exception.
         */
        try {
            match = matchRepository.saveAndFlush(match);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Lost the insert race for pair {} / {} - reading the winning row", userOne, userTwo);
            return matchRepository.findByPair(userOne, userTwo);
        }

        Conversation conversation = conversationRepository.save(Conversation.builder()
                .matchId(match.getId())
                .userAId(match.getUserAId())
                .userBId(match.getUserBId())
                .build());
        match.setConversationId(conversation.getId());
        matchRepository.save(match);

        notifyBothSides(match, source);
        log.info("Match {} created between {} and {} via {}", match.getId(), userOne, userTwo, source);
        return Optional.of(match);
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> listMatches(UUID userId, Pageable pageable) {
        Page<Match> matches = matchRepository.findAllForUser(userId, MatchStatus.ACTIVE, pageable);
        return PageResponse.of(toResponses(userId, matches.getContent()),
                matches.getNumber(), matches.getSize(), matches.getTotalElements());
    }

    /**
     * The match between two people, as one of them sees it.
     *
     * <p>Used when replaying an idempotent like: the original request already created the
     * match, so the retry has to return the same payload rather than a null.
     */
    @Transactional(readOnly = true)
    public Optional<MatchResponse> findMatchFor(UUID viewerId, UUID otherUserId) {
        return matchRepository.findByPair(viewerId, otherUserId)
                .map(match -> toResponses(viewerId, List.of(match)))
                .flatMap(responses -> responses.stream().findFirst());
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(UUID userId, UUID matchId) {
        Match match = requireParticipant(userId, matchId);
        return toResponses(userId, List.of(match)).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Match", matchId));
    }

    @Transactional(readOnly = true)
    public long activeMatchCount(UUID userId) {
        return matchRepository.countForUser(userId, MatchStatus.ACTIVE);
    }

    @Transactional
    public void unmatch(UUID userId, UUID matchId, String reason, boolean alsoBlock) {
        Match match = requireParticipant(userId, matchId);
        UUID other = match.otherParticipant(userId);

        match.setStatus(MatchStatus.UNMATCHED);
        match.setUnmatchedBy(userId);
        match.setUnmatchedAt(Instant.now());
        matchRepository.save(match);

        conversationRepository.findByMatchId(matchId).ifPresent(conversation -> {
            conversation.setStatus(Conversation.ConversationStatus.CLOSED);
            conversationRepository.save(conversation);
        });

        if (alsoBlock) {
            blockService.block(userId, other, reason);
        }
        log.info("User {} unmatched {} (match {}, block={})", userId, other, matchId, alsoBlock);
    }

    @Transactional(readOnly = true)
    public Match requireParticipant(UUID userId, UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match", matchId));
        if (!match.involves(userId)) {
            throw new ForbiddenException("This match does not belong to you");
        }
        return match;
    }

    @Transactional
    public void touchInteraction(UUID matchId) {
        // Single UPDATE: this runs on every message sent, so no load-then-save round trip.
        matchRepository.touchLastInteraction(matchId, Instant.now());
    }

    // ---- internals -----------------------------------------------------

    /**
     * Assembles the list rows. Three bulk reads (users, summaries, conversations) regardless
     * of page size - nothing here scales with the number of matches on the page.
     */
    private List<MatchResponse> toResponses(UUID viewerId, List<Match> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }
        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", viewerId));

        List<UUID> otherIds = matches.stream().map(m -> m.otherParticipant(viewerId)).toList();
        Map<UUID, UserSummaryResponse> summaries = userSummaryService.summariesFor(otherIds, viewer);

        List<UUID> conversationIds = matches.stream()
                .map(Match::getConversationId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<UUID, Conversation> conversations = conversationRepository.findAllById(conversationIds).stream()
                .collect(java.util.stream.Collectors.toMap(Conversation::getId, c -> c));

        List<MatchResponse> responses = new ArrayList<>(matches.size());
        for (Match match : matches) {
            UUID otherId = match.otherParticipant(viewerId);
            UserSummaryResponse summary = summaries.get(otherId);
            if (summary == null) {
                continue; // account removed since the match was made
            }
            Conversation conversation = match.getConversationId() == null
                    ? null : conversations.get(match.getConversationId());

            boolean started = conversation != null && conversation.getLastMessageAt() != null;
            responses.add(new MatchResponse(
                    match.getId(),
                    withScore(summary, match.getCompatibilityScore()),
                    match.getSource(),
                    match.getStatus(),
                    match.getMatchedAt(),
                    match.getCompatibilityScore(),
                    splitHighlights(match.getHighlights()),
                    match.getConversationId(),
                    started,
                    conversation == null ? null : conversation.getLastMessagePreview(),
                    conversation == null ? null : conversation.getLastMessageAt(),
                    conversation == null ? 0 : conversation.unreadFor(viewerId),
                    !started));
        }
        return responses;
    }

    private UserSummaryResponse withScore(UserSummaryResponse summary, Double score) {
        if (score == null) {
            return summary;
        }
        return new UserSummaryResponse(
                summary.userId(), summary.displayName(), summary.age(), summary.city(),
                summary.distanceKm(), summary.primaryPhotoUrl(), summary.blurhash(),
                summary.photoVerified(), summary.recentlyActive(), score);
    }

    private List<String> splitHighlights(String packed) {
        return packed == null || packed.isBlank() ? List.of() : List.of(packed.split("\\|"));
    }

    private void notifyBothSides(Match match, MatchSource source) {
        boolean auto = source == MatchSource.AUTO_MATCH_WEEKLY || source == MatchSource.AUTO_MATCH_DAILY;
        NotificationType type = auto ? NotificationType.AUTO_MATCH_READY : NotificationType.NEW_MATCH;
        String title = auto ? "Your match is ready" : "It is a match";

        notificationService.notifyAsync(match.getUserAId(), type, title,
                "Say hello and start the conversation", match.getUserBId(),
                "MATCH", match.getId(), null);
        notificationService.notifyAsync(match.getUserBId(), type, title,
                "Say hello and start the conversation", match.getUserAId(),
                "MATCH", match.getId(), null);
    }
}
