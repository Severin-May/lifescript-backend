package com.lifescript.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.ZoneId;

public final class TimeZones {

    private TimeZones() {
    }

    // null/blank = the server's zone. A bad zone is the client's bug, not the user's plan, so it's a 400.
    public static ZoneId parse(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone '" + timeZone + "'");
        }
    }
}
