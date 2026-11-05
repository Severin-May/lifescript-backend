package com.lifescript.api;

import org.springframework.stereotype.Component;

// Who is calling. One fixed local owner until accounts exist; then only this class changes.
@Component
public class CurrentUser {

    public String id() {
        return "local";
    }
}
