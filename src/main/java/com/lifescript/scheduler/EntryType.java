package com.lifescript.scheduler;

public enum EntryType {
    ROUTINE,
    EVENT,
    TASK,
    // The rest after a task; its name is the task's.
    BREAK
}
