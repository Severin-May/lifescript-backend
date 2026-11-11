package com.lifescript.planning;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// algorithm: greedy, csp or hybrid. timeLimit: how long csp and hybrid may search (greedy needs none).
@ConfigurationProperties(prefix = "lifescript.scheduler")
public record SchedulerProperties(String algorithm, Duration timeLimit) {

    public SchedulerProperties {
        algorithm = algorithm == null ? "greedy" : algorithm;
        timeLimit = timeLimit == null ? Duration.ofSeconds(2) : timeLimit;
    }
}
