package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

// One line of an energy profile block, e.g. "morning: high" or "14:00-16:00: low",
// with named periods already resolved through settings. location is where the line was written.
public record EnergyEntry(TimeRange range, EnergyLevel level, SourceLocation location) {
}
