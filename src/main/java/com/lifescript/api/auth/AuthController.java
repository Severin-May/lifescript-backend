package com.lifescript.api.auth;

import com.lifescript.account.AccountPrincipal;
import com.lifescript.account.AccountService;
import com.lifescript.account.LoginSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final LoginSessions sessions;

    public AuthController(AccountService accounts, LoginSessions sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
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
        return AccountResponse.from(sessions.start(account, request, response));
    }

    // A wrong email and a wrong password give the same answer (see ApiExceptionHandler).
    @PostMapping("/login")
    public AccountResponse login(@RequestBody CredentialsRequest credentials,
                                 HttpServletRequest request, HttpServletResponse response) {
        return AccountResponse.from(sessions.logIn(credentials.email(), credentials.password(), request, response));
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal account) {
        return AccountResponse.from(account);
    }
}
