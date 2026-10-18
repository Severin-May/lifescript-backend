package com.lifescript.model;

// One line of an energy profile block, e.g. "morning: high" or "14:00-16:00: low",
// with named periods already resolved through settings.
public record EnergyEntry(TimeRange range, EnergyLevel level) {
}
