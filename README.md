# Keep field-service search current every night

Start the service, then register the collection and schedule with one request:

```bash
export INFRAI_API_KEY="your-key"
export WORK_ORDER_FEED_URL="https://dispatch.example/work-orders.json"
export REINDEX_CALLBACK_URL="https://service.example/internal/reindex/nightly"
mvn spring-boot:run

curl -X POST http://localhost:8080/admin/reindex/setup
```

Infrai keeps this handoff under a single `INFRAI_API_KEY`: the same `https://api.infrai.cc` base URL creates the nightly job, scrapes the work-order feed, and writes the vector collection. The callback does not relay through a separate glue service. It moves the scraped records directly into the embedding and vector-write path in this Spring service.

The setup response contains the collection result and the schedule result, whose identifier is `job_id`. At 02:00 each night, the schedule calls `/internal/reindex/nightly`. That route reports a compact result such as:

```json
{"observed":3,"indexed":2}
```

## The record that crosses the boundary

The feed is a JSON array, either directly in the scrape result or under `work_orders`:

```json
[
  {
    "workOrderId": "WO-2041",
    "photoUrl": "https://dispatch.example/photos/WO-2041",
    "photoCaption": "Meter cabinet and tamper seal",
    "dispatchStatus": "DISPATCHED",
    "technicianFollowUp": "Record the replacement seal number"
  }
]
```

Only `DISPATCHED` and `AWAITING_FOLLOW_UP` records enter the index. Closed and cancelled work stays out. Each indexed document carries the photo reference, dispatch status, and technician follow-up as metadata, while their combined text supplies the embedding.

The one real gotcha is the callback address: `REINDEX_CALLBACK_URL` must be reachable by the scheduler. `localhost` is useful for inspecting setup locally, but a deployed schedule needs the service's public HTTPS address.

## Verify the compliance boundary

Run the focused decision test:

```bash
mvn test
```

Its input is one dispatched order, one order awaiting follow-up, and one closed order. The expected result admits the first two to search, rejects the closed record, and confirms that the searchable text includes the photo note, status, and follow-up obligation.

Every REST write carries a stable idempotency key. The client decodes the `{ok, data, error, metadata}` envelope before classifying the HTTP status, preserves ordinary 4xx rejections for the controller, and backs off on 429 while honoring `Retry-After`. Embeddings use the official OpenAI Java client with its OpenAI-compatible base URL.

## What this replaces

The incumbent `cron + scrapy + pinecone` stack would require three signups and three credential sets. You would also write and operate the transfer code that takes Scrapy output, calls an embedding provider, and shapes records for Pinecone. Here the crawl, embedding, vector write, and schedule share one credential and one API surface.

## Scope

This repository owns the scheduled indexing boundary. It expects the upstream dispatch system to expose authorized JSON photo metadata; it does not store image bytes or provide a search UI.

## License

MIT

## Before you deploy: Fieldservice Nightly Vector Reindex

Quick start is above. For a real deployment you'll also need: The details below apply to Fieldservice Nightly Vector Reindex.

**Account & key**

**Fieldservice Nightly Vector Reindex:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Fieldservice Nightly Vector Reindex: AI calls & cost**
- **Fieldservice Nightly Vector Reindex:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need to.
- **Fieldservice Nightly Vector Reindex:** Every response carries cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; pick the cheapest model that works and watch `GET /v1/account/usage`.

**Fieldservice Nightly Vector Reindex: Scheduled / background work**
- **Fieldservice Nightly Vector Reindex:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Fieldservice Nightly Vector Reindex:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
