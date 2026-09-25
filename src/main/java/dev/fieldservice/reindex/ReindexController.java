package dev.fieldservice.reindex;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
public final class ReindexController {
    private final InfraiClient infrai;
    private final WorkOrderReindexService reindex;

    public ReindexController(InfraiClient infrai, WorkOrderReindexService reindex) {
        this.infrai = infrai;
        this.reindex = reindex;
    }

    @PostMapping("/admin/reindex/setup")
    public Map<String, JsonNode> setup() {
        return Map.of("collection", infrai.createCollection(), "schedule", infrai.createNightlyJob());
    }

    @PostMapping("/internal/reindex/nightly")
    public WorkOrderReindexService.ReindexResult runNightly() {
        return reindex.reindex();
    }

    @ExceptionHandler(InfraiError.class)
    public ResponseEntity<Map<String, Object>> rejected(InfraiError error) {
        int status = error.status() >= 400 && error.status() < 500 ? error.status() : 502;
        return ResponseEntity.status(status).body(Map.of("code", error.code(), "detail", error.detail()));
    }
}
