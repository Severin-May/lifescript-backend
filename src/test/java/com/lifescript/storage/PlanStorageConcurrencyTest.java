package com.lifescript.storage;

import com.lifescript.TestDatabase;
import com.lifescript.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestDatabase.class)
@SpringBootTest
class PlanStorageConcurrencyTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
    private static final String DRAFT = "plan: p\n";

    @Autowired
    private PlanStorageService storage;

    @Autowired
    private SavedPlanRepository plans;

    @Autowired
    private AccountService accounts;

    @Autowired
    private TransactionTemplate transactions;

    private UUID owner;

    @BeforeEach
    void setUp() {
        owner = newAccount();
    }

    private UUID newAccount() {
        return accounts.register(UUID.randomUUID() + "@test.example", "correct horse").id();
    }

    // Starts all tasks at the same moment and returns what each one produced or threw.
    private static <T> List<Object> runTogether(List<Callable<T>> tasks) throws Exception {
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void manySavesOnTheSameVersion_atOnce_exactlyOneWins() throws Exception {
        UUID id = storage.create(owner, "plan", DRAFT, ZONE).getId();
        List<Callable<SavedPlan>> saves = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String name = "tab " + i;
            saves.add(() -> storage.update(owner, id, 0, name, DRAFT, ZONE));
        }

        List<Object> outcomes = runTogether(saves);

        long wins = outcomes.stream().filter(SavedPlan.class::isInstance).count();
        long conflicts = outcomes.stream().filter(PlanConflictException.class::isInstance).count();
        assertEquals(1, wins, () -> "Outcomes: " + outcomes);
        assertEquals(9, conflicts, () -> "Outcomes: " + outcomes);
        assertEquals(1L, storage.get(owner, id).getVersion());
        assertEquals(2, storage.history(owner, id).size());
    }

    @Test
    void aWriteBasedOnAStaleRead_isStoppedByTheDatabase() throws Exception {
        UUID id = storage.create(owner, "plan", DRAFT, ZONE).getId();
        CountDownLatch aHasRead = new CountDownLatch(1);
        CountDownLatch bHasSaved = new CountDownLatch(1);
        AtomicReference<Throwable> aFailure = new AtomicReference<>();

        // A reads version 0, waits until B has saved, then writes: past the service's own version check.
        Thread a = new Thread(() -> {
            try {
                transactions.executeWithoutResult(status -> {
                    SavedPlan stale = plans.findById(id).orElseThrow();
                    aHasRead.countDown();
                    await(bHasSaved);
                    stale.change("A", DRAFT, false, Instant.now());
                    plans.saveAndFlush(stale);
                });
            } catch (Throwable t) {
                aFailure.set(t);
            }
        });
        a.start();
        await(aHasRead);
        storage.update(owner, id, 0, "B", DRAFT, ZONE);
        bHasSaved.countDown();
        a.join(30_000);

        assertInstanceOf(OptimisticLockingFailureException.class, aFailure.get());
        SavedPlan current = storage.get(owner, id);
        assertEquals("B", current.getName());
        assertEquals(1L, current.getVersion());
    }

    @Test
    void manyAccountsSavingAtOnce_eachSeesOnlyTheirOwnPlan() throws Exception {
        List<UUID> owners = new ArrayList<>();
        List<Callable<SavedPlan>> creates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID account = newAccount();
            owners.add(account);
            String name = "plan of " + account;
            creates.add(() -> storage.create(account, name, DRAFT, ZONE));
        }

        List<Object> outcomes = runTogether(creates);

        assertTrue(outcomes.stream().allMatch(SavedPlan.class::isInstance), () -> "Outcomes: " + outcomes);
        for (UUID account : owners) {
            List<SavedPlan> theirs = storage.list(account);
            assertEquals(1, theirs.size());
            assertEquals("plan of " + account, theirs.get(0).getName());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other thread");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
