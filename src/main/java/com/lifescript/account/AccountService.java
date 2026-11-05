package com.lifescript.account;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class AccountService implements UserDetailsService {

    // Length rules as in NIST SP 800-63B; no forced mix of character types.
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 254;
    // Only a sanity check; whether the address really exists isn't checked.
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public AccountPrincipal register(String email, String password) {
        String normalized = normalize(email);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
            throw new InvalidAccountException("That doesn't look like an email address");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidAccountException("A password needs " + MIN_PASSWORD_LENGTH + " to "
                    + MAX_PASSWORD_LENGTH + " characters");
        }
        if (accounts.existsByEmail(normalized)) {
            throw new EmailTakenException();
        }
        Account account = accounts.save(new Account(UUID.randomUUID(), normalized, passwordEncoder.encode(password),
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        return principal(account);
    }

    // Used by Spring Security when someone logs in.
    @Override
    @Transactional(readOnly = true)
    public AccountPrincipal loadUserByUsername(String email) {
        return accounts.findByEmail(normalize(email)).map(AccountService::principal)
                .orElseThrow(() -> new UsernameNotFoundException("No account"));
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static AccountPrincipal principal(Account account) {
        return new AccountPrincipal(account.getId(), account.getEmail(), account.getPasswordHash());
    }
}
