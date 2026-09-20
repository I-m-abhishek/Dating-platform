package com.dating.platform.profile.service;

import com.dating.platform.common.exception.BusinessException;
import com.dating.platform.common.exception.ErrorCode;
import com.dating.platform.common.exception.ResourceNotFoundException;
import com.dating.platform.profile.dto.PromptAnswerResponse;
import com.dating.platform.profile.dto.UpsertPromptAnswerRequest;
import com.dating.platform.profile.entity.Prompt;
import com.dating.platform.profile.entity.PromptAnswer;
import com.dating.platform.profile.mapper.ProfileMapper;
import com.dating.platform.profile.repository.PromptAnswerRepository;
import com.dating.platform.profile.repository.PromptRepository;
import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Prompt answers - the short written answers that carry most of a profile's personality.
 *
 * <p>Capped at {@link #MAX_ANSWERS}: past three, people stop reading and profiles turn into
 * essays.
 */
@Service
@RequiredArgsConstructor
public class PromptAnswerService {

    public static final int MAX_ANSWERS = 3;

    private final PromptAnswerRepository promptAnswerRepository;
    private final PromptRepository promptRepository;
    private final UserRepository userRepository;
    private final ProfileMapper profileMapper;
    private final ProfileService profileService;

    @Transactional(readOnly = true)
    public List<PromptAnswerResponse> listOwn(UUID userId) {
        return profileMapper.toPrompts(promptAnswerRepository.findAllByUserIdOrderByDisplayOrderAsc(userId));
    }

    @Transactional
    public List<PromptAnswerResponse> upsert(UUID userId, UpsertPromptAnswerRequest request) {
        Prompt prompt = promptRepository.findById(request.promptId())
                .orElseThrow(() -> new ResourceNotFoundException("Prompt", request.promptId()));

        List<PromptAnswer> existing = promptAnswerRepository.findAllByUserIdOrderByDisplayOrderAsc(userId);
        PromptAnswer answer = existing.stream()
                .filter(a -> a.getPrompt().getId().equals(prompt.getId()))
                .findFirst()
                .orElse(null);

        if (answer == null) {
            if (existing.size() >= MAX_ANSWERS) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "You can answer at most " + MAX_ANSWERS + " prompts - replace one first");
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));
            answer = PromptAnswer.builder()
                    .user(user)
                    .prompt(prompt)
                    .displayOrder(existing.size())
                    .build();
        }

        answer.setAnswer(request.answer().trim());
        if (request.displayOrder() > 0) {
            answer.setDisplayOrder(request.displayOrder());
        }
        promptAnswerRepository.save(answer);
        profileService.recalculateCompleteness(userId);

        return listOwn(userId);
    }

    @Transactional
    public List<PromptAnswerResponse> delete(UUID userId, UUID answerId) {
        PromptAnswer answer = promptAnswerRepository.findByIdAndUserId(answerId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt answer", answerId));
        promptAnswerRepository.delete(answer);
        profileService.recalculateCompleteness(userId);
        return listOwn(userId);
    }
}
