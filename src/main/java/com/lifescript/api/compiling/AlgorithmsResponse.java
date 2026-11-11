package com.lifescript.api.compiling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifescript.planning.Schedulers;

import java.util.List;

public record AlgorithmsResponse(List<String> algorithms, @JsonProperty("default") String defaultAlgorithm) {

    public static AlgorithmsResponse from(Schedulers schedulers) {
        return new AlgorithmsResponse(schedulers.names(), schedulers.defaultName());
    }
}
