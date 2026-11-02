package com.lifescript.api;

public class CompileRequest {
    private String content;
    // The browser's IANA time zone, e.g. "Europe/Budapest"; optional (the server's zone is used then).
    private String timeZone;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
