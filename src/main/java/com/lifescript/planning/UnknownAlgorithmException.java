package com.lifescript.planning;

import java.util.List;

public class UnknownAlgorithmException extends RuntimeException {

    public UnknownAlgorithmException(String name, List<String> known) {
        super(String.format("Unknown algorithm '%s' (use %s)", name, joinNames(known)));
    }

    // [a, b, c] -> "a, b or c"
    private static String joinNames(List<String> names) {
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
    }
}
