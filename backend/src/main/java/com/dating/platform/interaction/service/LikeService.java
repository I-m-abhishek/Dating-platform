package com.dating.platform.interaction.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.interaction.dto.InboundLikeResponse;
import com.dating.platform.interaction.dto.LikeRequest;
import com.dating.platform.interaction.dto.LikeResultResponse;
import com.dating.platform.interaction.dto.LikesOverviewResponse;
import com.dating.platform.interaction.entity.Like;
import com.dating.platform.interaction.entity.LikeStatus;
import com.dating.platform.interaction.entity.Pass;
import com.dating.platform.interaction.repository.LikeRepository;
import com.dating.platform.interaction.repository.PassRepository;
import com.dating.platform.match.entity.Match;
import com.dating.platform.match.entity.MatchSource;
import com.dating.platform.match.service.MatchService;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.service.NotificationService;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.service.UserSummaryService;
import com.dating.platform.quota.entity.QuotaFeature;
import com.dating.platform.quota.service.QuotaService;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.subscription.dto.Entitlements;
import com.dating.platform.subscription.entity.Feature;
import com.dating.platform.subscription.service.EntitlementService;
import com.dating.platform.user.dto.UserSummaryResponse;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Likes, passes, and the Likes You tab.
 *
 * <p>Two rules live here and nowhere else:
 * <ul>
 *   <li>A like that meets an existing inbound like becomes a match, atomically.</li>
 *   <li>Inbound likes are redacted server side unless the viewer has
 *       {@link Feature#SEE_WHO_LIKES_YOU}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PassRepository passRepository;
    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final MatchService matchService;
    private final BlockService blockService;
    private final QuotaService quotaService;
    private final EntitlementService entitlementService;
    private final UserSummaryService userSummaryService;
    private final NotificationService notificationService;

    @Transactional
    public LikeResultResponse like(UUID senderId, LikeRequest request) {
        UUID receiverId = request.targetUserId();
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.SELF_INTERACTION, "You cannot like yourself");
        }
        blockService.assertNotBlocked(senderId, receiverId);
        userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", receiverId));

        Entitlements entitlements = entitlementService.entitlementsOf(senderId);

        /*
         * Idempotency, before anything is spent.
         *
         * A retry carries the same clientLikeId as the original, so it replays the original
         * outcome instead of being charged again. This has to run ahead of the quota
         * consume: the old order spent an allowance first and only then discovered the like
         * was a duplicate, so a flaky connection cost the user a like AND returned an error.
         */
        if (StringUtils.hasText(request.clientLikeId())) {
            Optional<Like> replay = likeRepository.findBySenderIdAndClientLikeId(
                    senderId, request.clientLikeId());
            if (replay.isPresent()) {
                Like original = replay.get();
                log.debug("Replaying like {} for idempotency key {}", original.getId(), request.clientLikeId());
                return new LikeResultResponse(
                        original.getId(),
                        original.getStatus() == LikeStatus.MATCHED,
                        original.getStatus() == LikeStatus.MATCHED
                                ? matchService.findMatchFor(senderId, receiverId).orElse(null)
                                : null,
                        remainingLikes(senderId, entitlements));
            }
        }

        Optional<Like> existing = likeRepository.findBySenderIdAndReceiverId(senderId, receiverId);
        if (existing.isPresent() && existing.get().getStatus() != LikeStatus.WITHDRAWN) {
            // Also checked before the quota consume, for the same reason.
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }

        quotaService.consume(senderId, QuotaFeature.LIKE, entitlements.likesPerDay(),
                "Upgrade for more likes every day");

        Like like = existing.orElseGet(() -> Like.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .build());
        like.setType(request.typeOrDefault());
        like.setStatus(LikeStatus.PENDING);
        like.setTargetPhotoId(request.targetPhotoId());
        like.setTargetPromptAnswerId(request.targetPromptAnswerId());
        like.setNote(request.note());
        like.setClientLikeId(request.clientLikeId());
        like.setSeen(false);
        like = likeRepository.save(like);

        // A pass followed by a like is a change of mind - clear the pass so the pair is not hidden.
        passRepository.deleteBySenderIdAndReceiverId(senderId, receiverId);

        Optional<Like> reciprocal = likeRepository.findBySenderIdAndReceiverId(receiverId, senderId)
                .filter(l -> l.getStatus() == LikeStatus.PENDING);

        if (reciprocal.isPresent()) {
            return completeMatch(senderId, receiverId, like, reciprocal.get(), entitlements);
        }

        notificationService.notifyAsync(receiverId, NotificationType.NEW_LIKE,
                "Someone likes you", request.note(), senderId, "LIKE", like.getId(), null);

        return new LikeResultResponse(like.getId(), false, null, remainingLikes(senderId, entitlements));
    }

    private LikeResultResponse completeMatch(UUID senderId, UUID receiverId, Like outbound, Like inbound,
                                             Entitlements entitlements) {
        outbound.setStatus(LikeStatus.MATCHED);
        inbound.setStatus(LikeStatus.MATCHED);
        likeRepository.saveAll(List.of(outbound, inbound));

        Match match = matchService.createMatch(senderId, receiverId, MatchSource.MUTUAL_LIKE, null, List.of())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Could not create the match"));

        return new LikeResultResponse(
                outbound.getId(),
                true,
                matchService.getMatch(senderId, match.getId()),
                remainingLikes(senderId, entitlements));
    }

    @Transactional
    public void pass(UUID senderId, UUID targetUserId) {
        if (senderId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_INTERACTION, "You cannot pass on yourself");
        }
        Pass pass = passRepository.findBySenderIdAndReceiverId(senderId, targetUserId)
                .orElseGet(() -> Pass.builder().senderId(senderId).receiverId(targetUserId).build());
        pass.setExpiresAt(Instant.now().plus(Pass.DEFAULT_COOLDOWN_DAYS, ChronoUnit.DAYS));
        passRepository.save(pass);

        // If they had liked us, mark it declined so it leaves their pending list.
        likeRepository.findBySenderIdAndReceiverId(targetUserId, senderId)
                .filter(l -> l.getStatus() == LikeStatus.PENDING)
                .ifPresent(l -> {
                    l.setStatus(LikeStatus.DECLINED);
                    likeRepository.save(l);
                });
    }

    /** Undo the most recent pass. Gated on {@link Feature#REWIND}. */
    @Transactional
    public UUID rewindLastPass(UUID userId) {
        entitlementService.require(userId, Feature.REWIND);
        Entitlements entitlements = entitlementService.entitlementsOf(userId);
        quotaService.consume(userId, QuotaFeature.REWIND, entitlements.rewindsPerDay(),
                "Upgrade for more rewinds");

        Pass last = passRepository.findFirstBySenderIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("There is nothing to rewind"));
        UUID restored = last.getReceiverId();
        passRepository.delete(last);
        return restored;
    }

    /**
     * The Likes You tab.
     *
     * <p>Redaction happens here, on the server, before serialisation: a free viewer never
     * receives the sender's name or photo URL in the payload at all.
     */
    @Transactional(readOnly = true)
    public LikesOverviewResponse inboundLikes(UUID userId, Pageable pageable) {
        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<UUID> hidden = blockService.hiddenUserIdsFor(userId);
        if (hidden.isEmpty()) {
            hidden = List.of(userId); // the IN clause needs a non-empty collection
        }

        Page<Like> page = likeRepository.findInbound(userId, LikeStatus.PENDING, hidden, pageable);
        boolean revealed = entitlementService.has(userId, Feature.SEE_WHO_LIKES_YOU);

        List<UUID> senderIds = page.getContent().stream().map(Like::getSenderId).toList();
        Map<UUID, UserSummaryResponse> summaries = revealed
                ? userSummaryService.summariesFor(senderIds, viewer)
                : Map.of();

        Map<UUID, String> photoUrls = revealed ? photoUrlsFor(page) : Map.of();

        List<InboundLikeResponse> rows = page.getContent().stream()
                .map(like -> revealed
                        ? revealedRow(like, summaries.get(like.getSenderId()), photoUrls)
                        : blurredRow(like))
                .filter(java.util.Objects::nonNull)
                .toList();

        Page<InboundLikeResponse> mapped = new PageImpl<>(rows, pageable, page.getTotalElements());

        return new LikesOverviewResponse(
                likeRepository.countInbound(userId, LikeStatus.PENDING),
                likeRepository.countUnseenInbound(userId, LikeStatus.PENDING),
                revealed,
                revealed ? null : "Upgrade to see everyone who likes you",
                PageResponse.from(mapped));
    }

    @Transactional
    public void markInboundSeen(UUID userId) {
        likeRepository.markInboundSeen(userId);
    }

    @Transactional(readOnly = true)
    public long unseenLikeCount(UUID userId) {
        return likeRepository.countUnseenInbound(userId, LikeStatus.PENDING);
    }

    /** Ids already acted on by this user - discovery must not show them again. */
    @Transactional(readOnly = true)
    public List<UUID> alreadyActedOn(UUID userId) {
        List<UUID> liked = likeRepository.findReceiverIdsBySender(userId);
        List<UUID> passed = passRepository.findActiveReceiverIds(userId, Instant.now());
        return java.util.stream.Stream.concat(liked.stream(), passed.stream()).distinct().toList();
    }

    // ---- redaction -----------------------------------------------------

    private InboundLikeResponse revealedRow(Like like, UserSummaryResponse summary, Map<UUID, String> photoUrls) {
        if (summary == null) {
            return null;
        }
        return new InboundLikeResponse(
                like.getId(),
                false,
                summary,
                like.getType(),
                like.getNote(),
                like.getTargetPhotoId(),
                like.getTargetPhotoId() == null ? null : photoUrls.get(like.getTargetPhotoId()),
                like.isSeen(),
                like.getCreatedAt());
    }

    /**
     * The locked view. Everything identifying is dropped: no name, no city, no photo URL.
     * Only the age bucket and the fact that a like exists survive, which is enough to make
     * the tab feel alive without giving the paid feature away.
     */
    private InboundLikeResponse blurredRow(Like like) {
        UserSummaryResponse placeholder = new UserSummaryResponse(
                null, null, 0, null, null, null, null, false, false, null);
        return new InboundLikeResponse(
                like.getId(),
                true,
                placeholder,
                like.getType(),
                null,
                null,
                null,
                like.isSeen(),
                like.getCreatedAt());
    }

    private Map<UUID, String> photoUrlsFor(Page<Like> page) {
        List<UUID> photoIds = page.getContent().stream()
                .map(Like::getTargetPhotoId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (photoIds.isEmpty()) {
            return Map.of();
        }
        return photoRepository.findAllById(photoIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.dating.platform.profile.entity.Photo::getId,
                        com.dating.platform.profile.entity.Photo::getUrl));
    }

    private int remainingLikes(UUID userId, Entitlements entitlements) {
        int limit = entitlements.likesPerDay();
        if (Entitlements.isUnlimited(limit)) {
            return -1;
        }
        return Math.max(0, limit - quotaService.used(userId, QuotaFeature.LIKE));
    }
}
