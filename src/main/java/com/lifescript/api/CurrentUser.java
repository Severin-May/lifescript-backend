package com.lifescript.api;

import com.lifescript.account.AccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Who is calling: the logged-in account. Only used behind endpoints that require login.
@Component
public class CurrentUser {

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal.id();
        }
        throw new IllegalStateException("No logged-in user; the endpoint should require login");
    }
}
