package com.lifescript.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SavedPlanRepository extends JpaRepository<SavedPlan, UUID> {

    List<SavedPlan> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<SavedPlan> findByIdAndOwnerId(UUID id, UUID ownerId);
}
