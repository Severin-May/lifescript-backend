package com.lifescript.api.auth;

import org.springframework.security.web.csrf.CsrfToken;

// headerName: where to send the token back, e.g. "X-XSRF-TOKEN".
public record CsrfResponse(String headerName, String token) {

    static CsrfResponse from(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }
}
