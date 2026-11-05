package com.lifescript.api;

// timeZone: the browser's, so "valid" is judged with the user's "today" (optional).
public record CreatePlanRequest(String name, String content, String timeZone) {
}
