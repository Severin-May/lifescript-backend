package com.lifescript.storage;

import com.lifescript.planning.PlanningService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// Every method works for one owner: someone else's plan behaves as if it didn't exist.
@Service
@Transactional
public class PlanStorageService {

    private static final int MAX_NAME_LENGTH = 200;

    private final SavedPlanRepository plans;
    private final SavedPlanVersionRepository versions;
    private final PlanningService planning;
    private final Clock clock;

    public PlanStorageService(SavedPlanRepository plans, SavedPlanVersionRepository versions,
                              PlanningService planning, Clock clock) {
        this.plans = plans;
        this.versions = versions;
        this.planning = planning;
        this.clock = clock;
    }

    // zone: the user's, so "valid" means what /api/validate would say for them.
    public SavedPlan create(String ownerId, String name, String content, ZoneId zone) {
        checkInput(name, content);
        SavedPlan plan = plans.saveAndFlush(new SavedPlan(UUID.randomUUID(), ownerId, name, content,
                isValid(content, zone), now()));
        versions.save(new SavedPlanVersion(plan));
        return plan;
    }

    @Transactional(readOnly = true)
    public SavedPlan get(String ownerId, UUID id) {
        return plans.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new PlanNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<SavedPlan> list(String ownerId) {
        return plans.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    // expectedVersion: the version the client loaded; anything saved since makes this fail.
    public SavedPlan update(String ownerId, UUID id, long expectedVersion, String name, String content, ZoneId zone) {
        checkInput(name, content);
        SavedPlan plan = get(ownerId, id);
        if (plan.getVersion() != expectedVersion) {
            throw new PlanConflictException(id, expectedVersion);
        }
        plan.change(name, content, isValid(content, zone), now());
        try {
            plans.saveAndFlush(plan);
        } catch (OptimisticLockingFailureException e) {
            // Someone saved between our read and our write.
            throw new PlanConflictException(id, expectedVersion);
        }
        versions.save(new SavedPlanVersion(plan));
        return plan;
    }

    public void delete(String ownerId, UUID id) {
        plans.delete(get(ownerId, id));
    }

    // Newest first.
    @Transactional(readOnly = true)
    public List<SavedPlanVersion> history(String ownerId, UUID id) {
        get(ownerId, id);
        return versions.findByPlanIdOrderByVersionDesc(id);
    }

    // PostgreSQL keeps microseconds; rounding here makes a saved plan's times equal to the ones loaded later.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private boolean isValid(String content, ZoneId zone) {
        return planning.validate(content, zone).diagnostics().isEmpty();
    }

    private static void checkInput(String name, String content) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidPlanException("A plan needs a name of 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (content == null) {
            throw new InvalidPlanException("A plan needs content");
        }
    }
}
