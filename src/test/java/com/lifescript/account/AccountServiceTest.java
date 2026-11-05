package com.lifescript.account;

import com.lifescript.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestDatabase.class)
@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void password_isStoredOnlyAsABcryptHash() {
        AccountPrincipal account = accounts.register(UUID.randomUUID() + "@test.example", "correct horse");

        String stored = repository.findById(account.id()).orElseThrow().getPasswordHash();
        assertTrue(stored.startsWith("{bcrypt}"), stored);
        assertFalse(stored.contains("correct horse"));
        assertTrue(passwordEncoder.matches("correct horse", stored));
    }

    @Test
    void email_isStoredLowercased_andTrimmed() {
        String email = UUID.randomUUID() + "@Test.Example";

        AccountPrincipal account = accounts.register("  " + email + " ", "correct horse");

        assertEquals(email.toLowerCase(), account.email());
    }

    @Test
    void theSessionsCopy_hasNoPasswordHash() {
        AccountPrincipal account = accounts.register(UUID.randomUUID() + "@test.example", "correct horse");

        assertNull(account.withoutPassword().passwordHash());
        assertEquals(account.id(), account.withoutPassword().id());
    }
}
