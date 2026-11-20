package com.lifescript.account;

import com.lifescript.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    // Both pass the "email exists?" check before either saves (the slow BCrypt hash sits in between); the
    // database's unique email then rejects one, which must still be reported as a taken email.
    @Test
    void sameEmailRegisteredTwiceAtOnce_oneGetsEmailTaken() throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                String email = UUID.randomUUID() + "@test.example";
                CyclicBarrier together = new CyclicBarrier(2);
                Callable<Object> attempt = () -> {
                    together.await();
                    try {
                        return accounts.register(email, "correct horse");
                    } catch (RuntimeException e) {
                        return e;
                    }
                };
                List<Future<Object>> results = threads.invokeAll(List.of(attempt, attempt), 30, TimeUnit.SECONDS);

                List<Object> outcomes = new ArrayList<>();
                for (Future<Object> result : results) {
                    outcomes.add(result.get());
                }
                assertEquals(1, outcomes.stream().filter(AccountPrincipal.class::isInstance).count(), outcomes::toString);
                assertEquals(1, outcomes.stream().filter(EmailTakenException.class::isInstance).count(), outcomes::toString);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void theSessionsCopy_hasNoPasswordHash() {
        AccountPrincipal account = accounts.register(UUID.randomUUID() + "@test.example", "correct horse");

        assertNull(account.withoutPassword().passwordHash());
        assertEquals(account.id(), account.withoutPassword().id());
    }
}
