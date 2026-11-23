package com.thiago.spacebooking.common;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, extracted from the JWT in the security context. */
public record CurrentUser(UUID id, boolean admin) {

    public static CurrentUser of(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return new CurrentUser(UUID.fromString(jwt.getSubject()), admin);
    }

    /** True when the request carries a valid token of an admin. Anonymous callers are not admins. */
    public static boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
