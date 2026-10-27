package com.lifescript.scheduler;

public record EnergyFit(int distance, int matchingSeconds) {

    // Fully equal fits: neither wins, so the caller keeps the earliest.
    public boolean isBetterThan(EnergyFit other) {
        if (distance != other.distance) {
            return distance < other.distance;
        }
        return matchingSeconds > other.matchingSeconds;
    }
}
