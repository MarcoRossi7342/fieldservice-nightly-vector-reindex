package dev.fieldservice.reindex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public final class WorkOrderReindexService {
    private final InfraiClient infrai;
    private final InfraiProperties config;
    private final ObjectMapper json;

    public WorkOrderReindexService(InfraiClient infrai, InfraiProperties config, ObjectMapper json) {
        this.infrai = infrai;
        this.config = config;
        this.json = json;
    }

    public ReindexResult reindex() {
        JsonNode feed = infrai.scrape(config.sourceUrl());
        List<WorkOrder> orders = readOrders(feed);
        List<ObjectNode> vectors = new ArrayList<>();
        for (WorkOrder order : orders) {
            if (!order.belongsInSearchIndex()) continue;
            ObjectNode vector = json.createObjectNode();
            vector.put("id", order.workOrderId());
            vector.set("values", json.valueToTree(infrai.embed(order.searchableText())));
            vector.set("metadata", json.valueToTree(order));
            vectors.add(vector);
        }
        if (!vectors.isEmpty()) infrai.upsert(vectors);
        return new ReindexResult(orders.size(), vectors.size());
    }

    private List<WorkOrder> readOrders(JsonNode scraped) {
        JsonNode content = scraped.has("content") ? scraped.path("content") : scraped;
        try {
            JsonNode records = content.isTextual() ? json.readTree(content.asText()) : content;
            if (records.has("work_orders")) records = records.path("work_orders");
            return json.readerForListOf(WorkOrder.class).readValue(records);
        } catch (Exception e) {
            throw new IllegalArgumentException("Work-order feed must contain a JSON array", e);
        }
    }

    public record ReindexResult(int observed, int indexed) {}
}
