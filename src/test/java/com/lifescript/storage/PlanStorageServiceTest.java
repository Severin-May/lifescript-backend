package com.lifescript.storage;

import com.lifescript.TestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestDatabase.class)
@SpringBootTest
class PlanStorageServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
    private static final String VALID = """
            plan: p
            period: 2030-01-07 to 2030-01-13

            availability:
            monday: flexible

            tasks:
            task: a
            duration: 1h
            priority: high
            effort: high
            """;
    private static final String DRAFT = "plan: p\n";

    @Autowired
    private PlanStorageService storage;

    @Autowired
    private SavedPlanVersionRepository versions;

    // Each test has its own owner, so tests sharing the database don't see each other's plans.
    private final String owner = UUID.randomUUID().toString();

    @Test
    void create_thenGet_returnsThePlan_atVersionZero() {
        SavedPlan created = storage.create(owner, "my week", VALID, ZONE);

        SavedPlan loaded = storage.get(owner, created.getId());
        assertEquals("my week", loaded.getName());
        assertEquals(VALID, loaded.getContent());
        assertTrue(loaded.isValid());
        assertEquals(0L, loaded.getVersion());
        assertEquals(created.getCreatedAt(), loaded.getCreatedAt());
    }

    @Test
    void draftWithErrors_isSaved_asInvalid() {
        assertFalse(storage.create(owner, "draft", DRAFT, ZONE).isValid());
    }

    @Test
    void update_withTheLoadedVersion_isSaved_andKeptInHistory() {
        SavedPlan plan = storage.create(owner, "draft", DRAFT, ZONE);

        SavedPlan updated = storage.update(owner, plan.getId(), 0, "my week", VALID, ZONE);

        assertEquals(1L, updated.getVersion());
        assertTrue(updated.isValid());
        List<SavedPlanVersion> history = storage.history(owner, plan.getId());
        assertEquals(List.of(1L, 0L), history.stream().map(SavedPlanVersion::getVersion).toList());
        assertEquals(List.of(VALID, DRAFT), history.stream().map(SavedPlanVersion::getContent).toList());
    }

    @Test
    void update_basedOnAnOlderVersion_isRefused_andChangesNothing() {
        SavedPlan plan = storage.create(owner, "my week", DRAFT, ZONE);
        storage.update(owner, plan.getId(), 0, "tab A", VALID, ZONE);

        // A second tab still has version 0.
        assertThrows(PlanConflictException.class,
                () -> storage.update(owner, plan.getId(), 0, "tab B", DRAFT, ZONE));

        SavedPlan current = storage.get(owner, plan.getId());
        assertEquals("tab A", current.getName());
        assertEquals(1L, current.getVersion());
        assertEquals(2, storage.history(owner, plan.getId()).size());
    }

    @Test
    void anotherOwner_cantSeeChangeOrDeleteThePlan() {
        UUID id = storage.create(owner, "mine", VALID, ZONE).getId();
        String stranger = UUID.randomUUID().toString();

        assertThrows(PlanNotFoundException.class, () -> storage.get(stranger, id));
        assertThrows(PlanNotFoundException.class, () -> storage.update(stranger, id, 0, "theirs", DRAFT, ZONE));
        assertThrows(PlanNotFoundException.class, () -> storage.delete(stranger, id));
        assertThrows(PlanNotFoundException.class, () -> storage.history(stranger, id));
        assertEquals(List.of(), storage.list(stranger));
        assertEquals("mine", storage.get(owner, id).getName());
    }

    @Test
    void list_hasOnlyTheOwnersPlans_lastChangedFirst() throws InterruptedException {
        SavedPlan first = storage.create(owner, "first", VALID, ZONE);
        Thread.sleep(5);
        SavedPlan second = storage.create(owner, "second", VALID, ZONE);
        Thread.sleep(5);
        storage.update(owner, first.getId(), 0, "first, edited", VALID, ZONE);
        storage.create(UUID.randomUUID().toString(), "someone else's", VALID, ZONE);

        assertEquals(List.of(first.getId(), second.getId()),
                storage.list(owner).stream().map(SavedPlan::getId).toList());
    }

    @Test
    void delete_removesThePlan_andItsHistory() {
        UUID id = storage.create(owner, "my week", VALID, ZONE).getId();
        storage.update(owner, id, 0, "my week", DRAFT, ZONE);

        storage.delete(owner, id);

        assertThrows(PlanNotFoundException.class, () -> storage.get(owner, id));
        assertEquals(List.of(), versions.findByPlanIdOrderByVersionDesc(id));
    }

    @Test
    void nameMustBeGiven_andNotTooLong() {
        assertThrows(InvalidPlanException.class, () -> storage.create(owner, " ", VALID, ZONE));
        assertThrows(InvalidPlanException.class, () -> storage.create(owner, "x".repeat(201), VALID, ZONE));
    }
}
