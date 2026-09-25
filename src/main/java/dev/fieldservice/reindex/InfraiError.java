package dev.fieldservice.reindex;

import com.fasterxml.jackson.databind.JsonNode;

public final class InfraiError extends RuntimeException {
    private final String code;
    private final JsonNode detail;
    private final int status;

    public InfraiError(String code, JsonNode detail, int status) {
        super(code + ": " + detail.path("message").asText("request rejected"));
        this.code = code;
        this.detail = detail;
        this.status = status;
    }

    public String code() { return code; }
    public JsonNode detail() { return detail; }
    public int status() { return status; }
}
