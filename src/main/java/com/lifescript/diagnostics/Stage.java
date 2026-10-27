package com.lifescript.diagnostics;

// Which part of the compiler found a problem, so the frontend can show or style them differently.
//   SYNTAX     - the text can't be read (parser)
//   VALIDATION - the text was read, but something in it doesn't make sense (validators)
//   INTERNAL   - the compiler itself failed: a bug in LifeScript, not in the plan
public enum Stage {
    SYNTAX,
    VALIDATION,
    INTERNAL
}
