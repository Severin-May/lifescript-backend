package com.lifescript.model;

import com.lifescript.diagnostics.SourceLocation;

public record EnergyEntry(TimeRange range, EnergyLevel level, SourceLocation location) {
}
