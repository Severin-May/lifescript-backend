package com.lifescript.api.plans;

import com.lifescript.account.AccountPrincipal;
import com.lifescript.api.TimeZones;
import com.lifescript.storage.InvalidPlanException;
import com.lifescript.storage.PlanStorageService;
import com.lifescript.storage.SavedPlan;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    public PlanController(PlanStorageService storage) {
        this.storage = storage;
    }

    // Every endpoint here requires login (SecurityConfig), so there's always an account.
    @GetMapping
    public List<PlanSummaryResponse> list(@AuthenticationPrincipal AccountPrincipal account) {
        return storage.list(account.id()).stream().map(PlanSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse get(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) {
        return PlanResponse.from(storage.get(account.id(), id));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@AuthenticationPrincipal AccountPrincipal account,
                                               @RequestBody CreatePlanRequest request) {
        SavedPlan plan = storage.create(account.id(), request.name(), request.content(),
                TimeZones.parse(request.timeZone()));
        return ResponseEntity.created(URI.create("/api/plans/" + plan.getId())).body(PlanResponse.from(plan));
    }

    @PutMapping("/{id}")
    public PlanResponse update(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id,
                               @RequestBody UpdatePlanRequest request) {
        if (request.version() == null) {
            throw new InvalidPlanException("Saving needs the version the plan was loaded at");
        }
        return PlanResponse.from(storage.update(account.id(), id, request.version(), request.name(),
                request.content(), TimeZones.parse(request.timeZone())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) {
        storage.delete(account.id(), id);
        return ResponseEntity.noContent().build();
    }

    // Newest first.
    @GetMapping("/{id}/history")
    public List<PlanVersionResponse> history(@AuthenticationPrincipal AccountPrincipal account, @PathVariable UUID id) {
        return storage.history(account.id(), id).stream().map(PlanVersionResponse::from).toList();
    }
}
