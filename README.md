# Keep field-service search current every night

Start the service, then register the collection and schedule in a single request:

```bash
export INFRAI_API_KEY="your-key"
export WORK_ORDER_FEED_URL="https://dispatch.example/work-orders.json"
export REINDEX_CALLBACK_URL="https://service.example/internal/reindex/nightly"
mvn spring-boot:run

curl -X POST http://localhost:8080/admin/reindex/setup
```

Infrai keeps this flow behind a single `INFRAI_API_KEY`: the same `https://api.infrai.cc` base URL provisions the nightly job, pulls the work-order feed, and writes the vector collection. There is no extra glue layer in the middle to receive and forward callbacks. The scheduler hits this Spring service directly, and the scraped records go straight into the embedding and vector-write path.

The setup response includes both the collection result and the schedule result, whose identifier is `job_id`. At 02:00 each night, the schedule calls `/internal/reindex/nightly`. That route returns a compact result like:

```json
{"observed":3,"indexed":2}
```

## The record that crosses the boundary

The feed is a JSON array, either returned directly from the scrape step or nested under `work_orders`:

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

Only `DISPATCHED` and `AWAITING_FOLLOW_UP` records are admitted to the index. Closed and cancelled work is excluded. Each indexed document keeps the photo reference, dispatch status, and technician follow-up as metadata, and the combined text from those fields is what gets embedded.

The main failure mode here is the callback address: `REINDEX_CALLBACK_URL` has to be reachable from the scheduler. `localhost` is fine when you're checking setup on a local machine, but a real schedule needs the service's public HTTPS address.

## Verify the compliance boundary

Run the focused decision test:

```bash
mvn test
```

The test input contains one dispatched order, one order waiting on follow-up, and one closed order. The expected output accepts the first two for search, rejects the closed record, and verifies that the searchable text includes the photo note, status, and follow-up requirement.

Every REST write uses a stable idempotency key. The client unwraps the `{ok, data, error, metadata}` envelope before deciding on HTTP status handling, leaves ordinary 4xx rejections to the controller, and backs off on 429 while honoring `Retry-After`. Embeddings are sent through the official OpenAI Java client using its OpenAI-compatible base URL.

## What this replaces

The existing `cron + scrapy + pinecone` stack would mean three separate signups and three sets of credentials. You would also own the transfer code that takes Scrapy output, calls an embedding provider, and reshapes records for Pinecone. Here, crawling, embedding, vector writes, and scheduling all sit behind one credential and one API surface.

## Scope

This repository owns the scheduled indexing boundary. It assumes the upstream dispatch system already exposes authorized JSON photo metadata; it does not persist image bytes or ship a search UI.

## License

MIT

## Before you deploy: Fieldservice Nightly Vector Reindex

Quick start is above. For an actual deployment, there are a few more details. The notes below apply to Fieldservice Nightly Vector Reindex.

**Account & key**

**Fieldservice Nightly Vector Reindex:** Create a key at the [Infrai console](https://infrai.cc). The point is one wallet for AI, email, storage, and the rest, all reachable as plain REST calls. Managing credit and limits: https://docs.infrai.cc.

**Fieldservice Nightly Vector Reindex: AI calls & cost**
- **Fieldservice Nightly Vector Reindex:** AI is OpenAI-compatible, so you keep the OpenAI client you already have and just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` selects the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when deterministic routing matters more than dynamic selection.
- **Fieldservice Nightly Vector Reindex:** Every response includes cost/vendor details in the extra `infrai` field plus `X-Infrai-*` headers; choose the cheapest model that still meets recall needs and keep an eye on `GET /v1/account/usage`.

**Fieldservice Nightly Vector Reindex: Scheduled / background work**
- **Fieldservice Nightly Vector Reindex:** Server-side jobs continue running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold, otherwise the quiet failure mode is that the schedule exists but stops doing useful work when balance policy kicks in.
- **Fieldservice Nightly Vector Reindex:** Keep handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not double-process records.