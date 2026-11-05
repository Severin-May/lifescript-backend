package com.lifescript.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

// A plan as stored: its LifeScript text. Mutable because JPA needs it; the compiled model.Plan stays read-only.
@Entity
@Table(name = "plans")
public class SavedPlan {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private String ownerId;

    private String name;
    private String content;
    private boolean valid;

    // Optimistic locking: null until first saved, then 0, 1, 2, ... one per save.
    @Version
    private Long version;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SavedPlan() {
    }

    SavedPlan(UUID id, String ownerId, String name, String content, boolean valid, Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.content = content;
        this.valid = valid;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void change(String name, String content, boolean valid, Instant now) {
        this.name = name;
        this.content = content;
        this.valid = valid;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
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

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
