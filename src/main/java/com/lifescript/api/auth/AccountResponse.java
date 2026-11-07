package com.lifescript.api.auth;

import com.lifescript.account.AccountPrincipal;

import java.util.UUID;

public record AccountResponse(UUID id, String email) {

    static AccountResponse from(AccountPrincipal principal) {
        return new AccountResponse(principal.id(), principal.email());
    }
}
