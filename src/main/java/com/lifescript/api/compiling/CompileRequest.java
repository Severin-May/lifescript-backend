package com.lifescript.api.compiling;

// timeZone: the browser's IANA zone, e.g. "Europe/Budapest"; optional (the server's zone is used then).
public record CompileRequest(String content, String timeZone) {
}
