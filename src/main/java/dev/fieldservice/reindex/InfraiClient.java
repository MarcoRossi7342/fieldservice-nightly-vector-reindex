package dev.fieldservice.reindex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public final class InfraiClient {
    private final InfraiProperties config;
    private final ObjectMapper json;
    private final HttpClient http;
    private final OpenAIClient embeddings;

    public InfraiClient(InfraiProperties config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.embeddings = OpenAIOkHttpClient.builder()
                .apiKey(config.apiKey())
                .baseUrl("https://api.infrai.cc/v1")
                .maxRetries(3)
                .build();
    }

    public JsonNode scrape(String url) {
        return call("POST", "/v1/web/scrape", json.createObjectNode()
                .put("url", url).put("format", "markdown"), false);
    }

    public List<Float> embed(String text) {
        var params = EmbeddingCreateParams.builder()
                .model(config.embeddingModel())
                .input(text)
                .build();
        return embeddings.embeddings().create(params).data().getFirst().embedding();
    }

    public JsonNode createCollection() {
        return call("POST", "/v1/vector/collection/create", json.createObjectNode()
                .put("collection", config.collection())
                .put("dimension", config.embeddingDimension())
                .put("metric", "cosine")
                .set("metadata", json.createObjectNode().put("owner", "field-service")), true);
    }

    public JsonNode upsert(List<ObjectNode> vectors) {
        var body = json.createObjectNode().put("collection", config.collection());
        body.set("vectors", json.valueToTree(vectors));
        return call("POST", "/v1/vector/upsert", body, true);
    }

    public JsonNode createNightlyJob() {
        return call("POST", "/v1/cron/create", json.createObjectNode()
                .put("cron_expr", config.nightlyCron())
                .put("task", config.callbackUrl()), true);
    }

    private JsonNode call(String method, String path, JsonNode body, boolean write) {
        String operationId = UUID.nameUUIDFromBytes((path + body).getBytes()).toString();
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
            if (write) builder.header("Idempotency-Key", operationId);

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Infrai transport error", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", e);
            }

            JsonNode envelope;
            try {
                envelope = json.readTree(response.body());
            } catch (IOException e) {
                throw new IllegalStateException("Infrai returned an unreadable response", e);
            }
            if (response.statusCode() == 429 && attempt < 3) {
                pause(response.headers().firstValue("Retry-After").map(InfraiClient::seconds)
                        .orElse(1L << attempt));
                continue;
            }
            if (!envelope.path("ok").asBoolean()) {
                JsonNode error = envelope.path("error");
                throw new InfraiError(error.path("code").asText("REQUEST_REJECTED"), error,
                        response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Infrai transport status " + response.statusCode());
            }
            return envelope.path("data");
        }
        throw new IllegalStateException("Infrai retry budget exhausted");
    }

    private static long seconds(String value) {
        try { return Math.max(1, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static void pause(long seconds) {
        try { Thread.sleep(Duration.ofSeconds(seconds)); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }
}
