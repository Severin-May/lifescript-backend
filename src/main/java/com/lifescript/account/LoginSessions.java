package com.lifescript.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

// Logging in means: check the password, then remember the account in the session (the JSESSIONID cookie).
@Component
public class LoginSessions {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public LoginSessions(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    // Throws AuthenticationException for a wrong email or password alike.
    public AccountPrincipal logIn(String email, String password, HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        AccountPrincipal account = (AccountPrincipal) authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password)).getPrincipal();
        return start(account, request, response);
    }

    // For an account whose password was just set, e.g. right after registering. Returns the session's copy.
    public AccountPrincipal start(AccountPrincipal account, HttpServletRequest request, HttpServletResponse response) {
        // A new session id on login, so an id someone planted before can't be used to take over the session.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        AccountPrincipal sessionPrincipal = account.withoutPassword();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sessionPrincipal, null,
                sessionPrincipal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return sessionPrincipal;
    }
}
