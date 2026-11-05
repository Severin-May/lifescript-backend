package com.lifescript.account;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// The logged-in user, as Spring Security keeps it in the session.
public record AccountPrincipal(UUID id, String email, String passwordHash) implements UserDetails {

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    // What the session keeps once logged in: no reason to carry the password hash around.
    public AccountPrincipal withoutPassword() {
        return new AccountPrincipal(id, email, null);
    }
}
