package com.lifescript.api;

import java.util.List;

public class CompileResponse {
    private List<String> errors;
    private boolean valid;

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
