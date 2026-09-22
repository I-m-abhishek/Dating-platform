package com.dating.platform.chat.controller;

import com.dating.platform.chat.dto.ConversationResponse;
import com.dating.platform.chat.dto.MessageResponse;
import com.dating.platform.chat.dto.SendMessageRequest;
import com.dating.platform.chat.service.ChatService;
import com.dating.platform.common.response.ApiResponse;
import com.dating.platform.common.response.CursorPageResponse;
import com.dating.platform.common.response.PageResponse;
import com.dating.platform.media.dto.MediaAssetResponse;
import com.dating.platform.media.service.MediaAssetService;
import com.dating.platform.ratelimit.RateLimit;
import com.dating.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Chat over HTTP. The same events also arrive live over STOMP; REST is the source of truth
 * and the socket is the accelerator, so a client with a dead socket still works.
 */
@Tag(name = "Chat", description = "Conversations, messages and attachments")
@Validated
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final MediaAssetService mediaAssetService;

    @Operation(summary = "List my conversations")
    @GetMapping
    public ApiResponse<PageResponse<ConversationResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.success(chatService.listConversations(principal.getId(), PageRequest.of(page, size)));
    }

    @Operation(summary = "Get one conversation",
            description = "Includes sendingState, which tells the client whether and why sending is allowed")
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(@AuthenticationPrincipal UserPrincipal principal,
                                                 @PathVariable UUID conversationId) {
        return ApiResponse.success(chatService.getConversation(principal.getId(), conversationId));
    }

    @Operation(summary = "Message history",
            description = "Newest first. Pass the cursor from the previous page as 'before'")
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<CursorPageResponse<MessageResponse>> messages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(chatService.messages(principal.getId(), conversationId, before, limit));
    }

    @Operation(summary = "Send a message",
            description = """
                    Refused with OPENER_LIMIT_REACHED when the caller opened the conversation
                    and has used their opener allowance without a reply.
                    """)
    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "chat.send", capacity = 120, period = 1, unit = TimeUnit.MINUTES)
    public ApiResponse<MessageResponse> send(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID conversationId,
                                             @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(chatService.send(principal.getId(), conversationId, request));
    }

    @Operation(summary = "Upload an attachment",
            description = "Returns an asset id to pass as attachmentIds when sending the message")
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(name = "chat.attachment", capacity = 60, period = 1, unit = TimeUnit.HOURS)
    public ApiResponse<MediaAssetResponse> uploadAttachment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer durationSeconds,
            @RequestParam(required = false) String waveform) {
        return ApiResponse.success(
                mediaAssetService.uploadChatAttachment(principal.getId(), file, durationSeconds, waveform));
    }

    @Operation(summary = "Mark a conversation as read")
    @PostMapping("/{conversationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID conversationId) {
        chatService.markRead(principal.getId(), conversationId);
        return ApiResponse.success((Void) null);
    }

    @Operation(summary = "Delete one of my messages")
    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ApiResponse<Void> deleteMessage(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID conversationId,
                                           @PathVariable UUID messageId) {
        chatService.deleteMessage(principal.getId(), conversationId, messageId);
        return ApiResponse.success("Message removed");
    }

    @Operation(summary = "Total unread count for the tab badge")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(Map.of("count", chatService.totalUnread(principal.getId())));
    }
}
