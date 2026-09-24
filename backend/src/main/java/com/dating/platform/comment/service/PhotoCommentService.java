package com.dating.platform.comment.service;

import com.dating.platform.comment.dto.CommentQuotaResponse;
import com.dating.platform.comment.dto.CommentResponse;
import com.dating.platform.comment.dto.CreateCommentRequest;
import com.dating.platform.comment.entity.PhotoComment;
import com.dating.platform.comment.repository.PhotoCommentRepository;
import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ForbiddenException;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.notification.entity.NotificationType;
import com.dating.platform.notification.service.NotificationService;
import com.dating.platform.profile.entity.Photo;
import com.dating.platform.profile.repository.PhotoRepository;
import com.dating.platform.profile.service.UserSummaryService;
import com.dating.platform.quota.entity.QuotaFeature;
import com.dating.platform.quota.service.QuotaService;
import com.dating.platform.safety.service.BlockService;
import com.dating.platform.subscription.dto.Entitlements;
import com.dating.platform.subscription.service.EntitlementService;
import com.dating.platform.user.dto.UserSummaryResponse;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import com.dating.platform.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comments on profile photos.
 *
 * <p><b>The daily allowance.</b> Free accounts get five comments a day
 * ({@code app.quota.free.photo-comments-per-day}); paid tiers get more. The counter is
 * consumed <em>before</em> the comment is written, inside the same transaction, so a failed
 * write gives the token back automatically on rollback and a burst of concurrent requests
 * cannot spend the same token twice.
 *
 * <p>Replies count against the same allowance - otherwise the limit would be trivially
 * bypassed by replying to yourself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoCommentService {

    private final PhotoCommentRepository commentRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final QuotaService quotaService;
    private final EntitlementService entitlementService;
    private final BlockService blockService;
    private final UserSummaryService userSummaryService;
    private final NotificationService notificationService;

    @Transactional
    public CommentResponse create(UUID authorId, UUID photoId, CreateCommentRequest request) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        UUID ownerId = photo.getUser().getId();

        if (ownerId.equals(authorId)) {
            throw new BusinessException(ErrorCode.SELF_INTERACTION, "You cannot comment on your own photo");
        }
        blockService.assertNotBlocked(authorId, ownerId);

        Entitlements entitlements = entitlementService.entitlementsOf(authorId);
        quotaService.consume(authorId, QuotaFeature.PHOTO_COMMENT, entitlements.photoCommentsPerDay(),
                "Upgrade to leave more comments every day");

        if (request.parentCommentId() != null) {
            PhotoComment parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment", request.parentCommentId()));
            if (!parent.getPhotoId().equals(photoId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "That comment belongs to a different photo");
            }
            commentRepository.incrementReplyCount(parent.getId());
        }

        PhotoComment comment = commentRepository.save(PhotoComment.builder()
                .photoId(photoId)
                .photoOwnerId(ownerId)
                .authorId(authorId)
                .body(request.body().trim())
                .parentCommentId(request.parentCommentId())
                .build());

        photoRepository.adjustCommentCount(photoId, 1);

        notificationService.notifyAsync(ownerId, NotificationType.NEW_PHOTO_COMMENT,
                "New comment on your photo", comment.getBody(), authorId,
                "PHOTO_COMMENT", comment.getId(), photo.getUrl());

        log.debug("User {} commented on photo {}", authorId, photoId);
        UserSummaryResponse author = userSummaryService.summariesFor(List.of(authorId), null).get(authorId);
        return toResponse(comment, authorId, author, List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listForPhoto(UUID viewerId, UUID photoId, Pageable pageable) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId));
        blockService.assertNotBlocked(viewerId, photo.getUser().getId());

        List<UUID> hiddenAuthors = blockService.hiddenUserIdsFor(viewerId);
        if (hiddenAuthors.isEmpty()) {
            hiddenAuthors = List.of(UUID.randomUUID()); // the IN clause needs a non-empty collection
        }

        Page<PhotoComment> page = commentRepository.findTopLevel(photoId, hiddenAuthors, pageable);
        User viewer = userRepository.findById(viewerId).orElse(null);

        // One query for every reply on the page rather than one per comment.
        List<UUID> withReplies = page.getContent().stream()
                .filter(c -> c.getReplyCount() > 0)
                .map(PhotoComment::getId)
                .toList();
        Set<UUID> hidden = Set.copyOf(hiddenAuthors);
        Map<UUID, List<PhotoComment>> repliesByParent = withReplies.isEmpty() ? Map.of()
                : commentRepository.findAllByParentCommentIdInAndHiddenFalseOrderByCreatedAtAsc(withReplies)
                .stream()
                .filter(reply -> !hidden.contains(reply.getAuthorId()))
                .collect(Collectors.groupingBy(PhotoComment::getParentCommentId));

        // Reply authors too - otherwise replies by anyone not also on the page have no author.
        List<UUID> authorIds = Stream.concat(
                        page.getContent().stream(),
                        repliesByParent.values().stream().flatMap(List::stream))
                .map(PhotoComment::getAuthorId)
                .distinct()
                .toList();
        Map<UUID, UserSummaryResponse> authors = userSummaryService.summariesFor(authorIds, viewer);

        List<CommentResponse> rows = page.getContent().stream()
                .map(comment -> {
                    List<CommentResponse> replies = repliesByParent.getOrDefault(comment.getId(), List.of())
                            .stream()
                            .map(reply -> toResponse(reply, viewerId, authors.get(reply.getAuthorId()), List.of()))
                            .toList();
                    return toResponse(comment, viewerId, authors.get(comment.getAuthorId()), replies);
                })
                .toList();

        return PageResponse.of(rows, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Comments left on the caller's own photos - the "activity" inbox. */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listForMyPhotos(UUID ownerId, Pageable pageable) {
        Page<PhotoComment> page =
                commentRepository.findAllByPhotoOwnerIdAndHiddenFalseOrderByCreatedAtDesc(ownerId, pageable);
        User owner = userRepository.findById(ownerId).orElse(null);
        Map<UUID, UserSummaryResponse> authors = userSummaryService.summariesFor(
                page.getContent().stream().map(PhotoComment::getAuthorId).distinct().toList(), owner);

        List<CommentResponse> rows = page.getContent().stream()
                .map(c -> toResponse(c, ownerId, authors.get(c.getAuthorId()), List.of()))
                .toList();
        return PageResponse.of(rows, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Either the author or the photo owner may remove a comment. */
    @Transactional
    public void delete(UUID requesterId, UUID commentId) {
        PhotoComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getAuthorId().equals(requesterId) && !comment.getPhotoOwnerId().equals(requesterId)) {
            throw new ForbiddenException("You cannot remove this comment");
        }
        if (comment.isHidden()) {
            return;
        }
        comment.setHidden(true);
        comment.setHiddenBy(requesterId);
        commentRepository.save(comment);
        photoRepository.adjustCommentCount(comment.getPhotoId(), -1);
    }

    /** Powers the "2 of 5 left today" hint and the paywall sheet. */
    @Transactional(readOnly = true)
    public CommentQuotaResponse quotaFor(UUID userId) {
        Entitlements entitlements = entitlementService.entitlementsOf(userId);
        int limit = entitlements.photoCommentsPerDay();
        int used = quotaService.used(userId, QuotaFeature.PHOTO_COMMENT);
        boolean unlimited = Entitlements.isUnlimited(limit);

        return new CommentQuotaResponse(
                limit,
                used,
                unlimited ? -1 : Math.max(0, limit - used),
                unlimited,
                DateUtils.nextDailyReset(),
                unlimited ? null : "Upgrade for more comments every day");
    }

    // ---- mapping -------------------------------------------------------

    private CommentResponse toResponse(PhotoComment comment, UUID viewerId,
                                       UserSummaryResponse author, List<CommentResponse> replies) {
        boolean canDelete = comment.getAuthorId().equals(viewerId) || comment.getPhotoOwnerId().equals(viewerId);
        return new CommentResponse(
                comment.getId(),
                comment.getPhotoId(),
                author,
                comment.getBody(),
                comment.getParentCommentId(),
                comment.getReplyCount(),
                replies,
                canDelete,
                comment.getCreatedAt());
    }
}
