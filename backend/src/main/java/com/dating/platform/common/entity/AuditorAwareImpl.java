package com.dating.platform.common.entity;

import com.dating.platform.security.SecurityUtils;
import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Feeds CreatedBy / LastModifiedBy from the security context. */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    @NonNull
    public Optional<UUID> getCurrentAuditor() {
        return SecurityUtils.currentUserIdOrEmpty();
    }
}
