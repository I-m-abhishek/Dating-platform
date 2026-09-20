package com.dating.platform.security;

import com.dating.platform.user.entity.User;
import com.dating.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Loads credentials for the {@code AuthenticationManager}.
 *
 * <p>The normal request path never calls this - {@link JwtAuthenticationFilter} builds the
 * principal from the token without touching the database. Defining it keeps Spring Boot
 * from auto-configuring an in-memory user with a random password at startup.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));

        return UserPrincipal.of(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.canAuthenticate(),
                user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()));
    }
}
