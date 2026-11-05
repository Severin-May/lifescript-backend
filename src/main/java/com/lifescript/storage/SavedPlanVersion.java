package com.lifescript.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

// One save of a plan, kept as history. Never changed after it's written.
@Entity
@Table(name = "plan_versions")
public class SavedPlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id")
    private UUID planId;

    private long version;
    private String name;
    private String content;
    private boolean valid;

    @Column(name = "saved_at")
    private Instant savedAt;

    protected SavedPlanVersion() {
    }

    SavedPlanVersion(SavedPlan plan) {
        this.planId = plan.getId();
        this.version = plan.getVersion();
        this.name = plan.getName();
        this.content = plan.getContent();
        this.valid = plan.isValid();
        this.savedAt = plan.getUpdatedAt();
    }

    public long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public boolean isValid() {
        return valid;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
