package com.lifescript.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SavedPlanVersionRepository extends JpaRepository<SavedPlanVersion, Long> {

    List<SavedPlanVersion> findByPlanIdOrderByVersionDesc(UUID planId);
}
