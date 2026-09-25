package dev.fieldservice.reindex;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infrai")
public record InfraiProperties(
        String apiKey,
        String baseUrl,
        String collection,
        String embeddingModel,
        int embeddingDimension,
        String sourceUrl,
        String callbackUrl,
        String nightlyCron) {
}
