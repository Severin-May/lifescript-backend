package com.lifescript.storage;

// The request itself is wrong (e.g. no name), not the plan's LifeScript text.
public class InvalidPlanException extends RuntimeException {

    public InvalidPlanException(String message) {
        super(message);
    }
}
