package com.lifescript.api;

// version: the one the client loaded; the save is refused (409) if the plan changed since.
public record UpdatePlanRequest(String name, String content, Long version, String timeZone) {
}
