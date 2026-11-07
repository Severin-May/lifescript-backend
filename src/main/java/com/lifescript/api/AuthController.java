package com.lifescript.api;

import com.lifescript.account.AccountPrincipal;
import com.lifescript.account.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Logging out is POST /api/auth/logout, handled by Spring Security (see SecurityConfig).
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AccountService accounts, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.accounts = accounts;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    // Asking for the token makes the XSRF-TOKEN cookie be set; the frontend calls this once at startup.
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return CsrfResponse.from(token);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@RequestBody CredentialsRequest credentials,
                                    HttpServletRequest request, HttpServletResponse response) {
        AccountPrincipal account = accounts.register(credentials.email(), credentials.password());
        return logIn(account, request, response);
    }

    // A wrong email and a wrong password give the same answer (see ApiExceptionHandler).
    @PostMapping("/login")
    public AccountResponse login(@RequestBody CredentialsRequest credentials,
                                 HttpServletRequest request, HttpServletResponse response) {
        AccountPrincipal account = (AccountPrincipal) authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email(), credentials.password()))
                .getPrincipal();
        return logIn(account, request, response);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal account) {
        return AccountResponse.from(account);
    }

    private AccountResponse logIn(AccountPrincipal account, HttpServletRequest request, HttpServletResponse response) {
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
        return AccountResponse.from(sessionPrincipal);
    }
}
