package com.lifescript.api.compiling;

// timeZone: the browser's IANA zone, e.g. "Europe/Budapest"; optional (the server's zone is used then).
// algorithm: one of GET /api/algorithms; optional (the configured default). Only /api/compile uses it.
public record CompileRequest(String content, String timeZone, String algorithm) {
}
