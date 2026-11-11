package com.lifescript.planning;

import com.lifescript.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The schedulers a request may pick by name. Keeps insertion order, so names() is stable for the UI.
public record Schedulers(Map<String, Scheduler> byName, String defaultName) {

    public Schedulers {
        byName = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
        if (!byName.containsKey(defaultName)) {
            throw new IllegalArgumentException("Default algorithm '" + defaultName + "' is not among " + byName.keySet());
        }
    }

    public List<String> names() {
        return new ArrayList<>(byName.keySet());
    }

    // null/blank = the default.
    public Scheduler find(String name) {
        if (name == null || name.isBlank()) {
            return byName.get(defaultName);
        }
        Scheduler scheduler = byName.get(name);
        if (scheduler == null) {
            throw new UnknownAlgorithmException(name, names());
        }
        return scheduler;
    }
}
