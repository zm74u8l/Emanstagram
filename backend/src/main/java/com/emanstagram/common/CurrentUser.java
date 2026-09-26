package com.emanstagram.common;

import com.emanstagram.user.User;
import com.emanstagram.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the caller's {@link User} from the security context.
 *
 * <p>Controllers take a {@code User} rather than a {@code UserDetails} so
 * services never need to re-query the database just to learn who is calling.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The authenticated account, or empty for anonymous requests. */
    @Transactional(readOnly = true)
    public Optional<User> get() {
        return currentUsername()
                .flatMap(name -> userRepository.findByUsernameIgnoreCase(name));
    }

    /** The authenticated account, or a 401. Use in endpoints that require auth. */
    @Transactional(readOnly = true)
    public User require() {
        return get().orElseThrow(() ->
                ApiException.unauthorized("UNAUTHENTICATED", "You need to sign in to do that."));
    }

    public Optional<UUID> id() {
        return get().map(User::getId);
    }

    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth.getPrincipal() instanceof String p && "anonymousUser".equals(p));
    }

    private Optional<String> currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UserDetails details)) {
            return Optional.empty();
        }
        return Optional.of(details.getUsername());
    }
}
