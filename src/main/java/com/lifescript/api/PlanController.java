package com.lifescript.api;

import com.lifescript.storage.InvalidPlanException;
import com.lifescript.storage.PlanStorageService;
import com.lifescript.storage.SavedPlan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanStorageService storage;
    private final CurrentUser currentUser;

    public PlanController(PlanStorageService storage, CurrentUser currentUser) {
        this.storage = storage;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<PlanSummaryResponse> list() {
        return storage.list(currentUser.id()).stream().map(PlanSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable UUID id) {
        return PlanResponse.from(storage.get(currentUser.id(), id));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@RequestBody CreatePlanRequest request) {
        SavedPlan plan = storage.create(currentUser.id(), request.name(), request.content(),
                TimeZones.parse(request.timeZone()));
        return ResponseEntity.created(URI.create("/api/plans/" + plan.getId())).body(PlanResponse.from(plan));
    }

    @PutMapping("/{id}")
    public PlanResponse update(@PathVariable UUID id, @RequestBody UpdatePlanRequest request) {
        if (request.version() == null) {
            throw new InvalidPlanException("Saving needs the version the plan was loaded at");
        }
        return PlanResponse.from(storage.update(currentUser.id(), id, request.version(), request.name(),
                request.content(), TimeZones.parse(request.timeZone())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        storage.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    // Newest first.
    @GetMapping("/{id}/history")
    public List<PlanVersionResponse> history(@PathVariable UUID id) {
        return storage.history(currentUser.id(), id).stream().map(PlanVersionResponse::from).toList();
    }
}
