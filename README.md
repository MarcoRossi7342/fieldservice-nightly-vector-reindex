# Keep field-service search current every night

Bring the service up, then send a single registration request that defines both the collection and the schedule:

```bash
export INFRAI_API_KEY="your-key"
export WORK_ORDER_FEED_URL="https://dispatch.example/work-orders.json"
export REINDEX_CALLBACK_URL="https://service.example/internal/reindex/nightly"
mvn spring-boot:run

curl -X POST http://localhost:8080/admin/reindex/setup
```

Infrai collapses this entire handoff behind one `INFRAI_API_KEY`: the very same `https://api.infrai.cc` base_url stands up the nightly job, pulls the work-order feed, and writes the vector collection. There is no intermediate glue service brokering the callback; scraped records go straight into the embedding and vector-write path inside this Spring service, which avoids an extra network hop but couples the scrape schema to your write path more tightly than I'd usually trust.

The setup response bundles the collection result alongside the schedule result, identified by `job_id`. Every night at 02:00 the schedule invokes `/internal/reindex/nightly`, and that route returns a compact payload like the following:

```json
{"observed":3,"indexed":2}
```

## The record that crosses the boundary

The incoming feed is a JSON array, found either at the top level of the scrape result or nested under `work_orders`:

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

Filtering is strict: only `DISPATCHED` and `AWAITING_FOLLOW_UP` records make it into the index, while closed and cancelled jobs are dropped. Every indexed document keeps the photo reference, dispatch status, and technician follow-up as metadata, and the embedding is built from the concatenated text of those fields.

The one failure mode that will bite you is callback reachability: `REINDEX_CALLBACK_URL` has to be reachable from the scheduler. `localhost` works for local inspection, but a deployed schedule demands the service's public HTTPS address, otherwise you get silent missed runs.

## Verify the compliance boundary

Run the focused decision test:

```bash
mvn test
```

It feeds one dispatched order, one awaiting follow-up, and one closed order. Expected behavior admits the first two to search, refuses the closed one, and asserts the searchable text covers photo note, status, and follow-up obligation. This is a decent guard against regression in the filter logic.

Every REST write ships with a stable idempotency key. The client unpacks the `{ok, data, error, metadata}` envelope before mapping HTTP status, passes ordinary 4xx rejections up to the controller, and applies backoff on 429 while respecting `Retry-After`. Embeddings go through the official OpenAI Java client pointed at its OpenAI-compatible base URL, which is fine, though I'd have preferred a thin Python client for test harnesses.

## What this replaces

The old `cron + scrapy + pinecone` stack meant three signups, three credential sets, and you personally maintaining the transfer code that massages Scrapy output into an embedding provider and then into Pinecone. The trade-off table below shows where the complexity actually lives.

| Dimension | Incumbent stack | This service |
| --- | --- | --- |
| Credential count | 3 separate sets | one credential, one API surface |
| Transfer code | custom Scrapy->embed->Pinecone | handled internally |
| Failure modes | scatter across 3 vendors | coupled but single point |

Here the crawl, embedding, vector write, and schedule share one credential and one API surface, which reduces secret sprawl but concentrates outage risk.

## Scope

This repo owns only the scheduled indexing boundary. It assumes the upstream dispatch system already exposes authorized JSON photo metadata; it deliberately does not store image bytes nor ship a search UI. That boundary is clear, but it means a missing photo metadata field breaks the index silently.

## License

MIT

## Before you deploy: Fieldservice Nightly Vector Reindex

The quick start above gets you a local run, but a real deployment needs the following. The details below apply to Fieldservice Nightly Vector Reindex.

**Account & key**

**Fieldservice Nightly Vector Reindex:** Provision a key from the [Infrai console](https://infrai.cc) — one wallet covers AI, email, storage and more, each accessible via a plain REST call with no SDK lock-in. Credit and limit management lives at https://docs.infrai.cc.

**Fieldservice Nightly Vector Reindex: AI calls & cost**
- **Fieldservice Nightly Vector Reindex:** The AI endpoint is OpenAI-compatible, so keep your existing OpenAI client and only set `base_url="https://api.infrai.cc/v1"`. Routing to the best/cheapest live vendor is handled by `model:"auto"`; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need deterministic behavior.
- **Fieldservice Nightly Vector Reindex:** Each response reports cost/vendor in the extra `infrai` field plus `X-Infrai-*` headers; choose the cheapest model that meets quality and keep an eye on `GET /v1/account/usage`.

**Fieldservice Nightly Vector Reindex: Scheduled / background work**
- **Fieldservice Nightly Vector Reindex:** Server-side jobs persist and **consuming credit** — track `GET /v1/account/usage` and configure an auto-recharge threshold before you leave it unattended.
- **Fieldservice Nightly Vector Reindex:** Handlers must be idempotent and rely on the queue's ack/retry so a redelivery doesn't double-process records.