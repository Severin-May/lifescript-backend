package com.lifescript.compiler;

import com.lifescript.grammar.LifeScriptParser;

// The one place that tells plan sections apart, so the validator and the builder can't disagree.
enum SectionKind {
    SETTINGS("settings"),
    AVAILABILITY("availability"),
    ENERGY_PROFILE("energy profile"),
    ROUTINES("routines"),
    TASKS("tasks"),
    EVENTS("events");

    private final String keyword;

    SectionKind(String keyword) {
        this.keyword = keyword;
    }

    String keyword() {
        return keyword;
    }

    static SectionKind of(LifeScriptParser.PlanSectionContext section) {
        if (section.settings() != null) return SETTINGS;
        if (section.availability() != null) return AVAILABILITY;
        if (section.energyProfile() != null) return ENERGY_PROFILE;
        if (section.routines() != null) return ROUTINES;
        if (section.tasks() != null) return TASKS;
        if (section.events() != null) return EVENTS;
        // Fail loudly if a section is added to the grammar but not here.
        throw new IllegalStateException("Unhandled section: " + section.getText());
    }
}
