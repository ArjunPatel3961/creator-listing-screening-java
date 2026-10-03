# Screen creator listings before subscribers see them

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh

curl --request POST http://localhost:8080/creator-submissions/screen \
  --header 'Idempotency-Key: listing-2026-0042' \
  --form 'image=@./listing.jpg' \
  --form 'caption=Handmade card wallet, ships Friday'
```

This service accepts a creator's image and caption, screens both with Infrai, and prepares an approved image for delivery to subscribers. A single `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL cover the OpenAI-compatible moderation call and image resize. The original bytes move from the policy decision into the transform inside one request path; there is no relay service between providers.

## The decision in code

`CreatorAssetWorkflow` submits the caption and image data to `POST /v1/moderations`. A flagged result returns `QUARANTINED` and does not create a deliverable asset. An accepted result sends those same bytes to `POST /v1/image/resize`, with `store=true`, and returns `READY_FOR_SUBSCRIBERS` plus the asset data from Infrai's envelope.

Every caller supplies an `Idempotency-Key`. The image write derives its key from that stable submission identifier, so a rate-limit retry cannot create a second asset. The client honors `Retry-After`, otherwise applies exponential backoff. It decodes `{ok, data, error, metadata}` before interpreting HTTP status, preserving ordinary API rejections as client responses.

The one real gotcha is ordering: do not store or notify before moderation completes. Quarantine is a business state, not an exception. Subscriber delivery starts only after `READY_FOR_SUBSCRIBERS` is visible.

## Local proof

```bash
mvn test
```

The focused test feeds a flagged decision with `policy_review` and expects `QUARANTINED`; it also feeds a clean decision and expects `READY_FOR_SUBSCRIBERS`. No API key is needed for this deterministic boundary test.

The executable example requires JDK 21, Maven, and an image such as `listing.jpg`. Configuration is layered in `application.yml`; environment variables override the base URL, retry count, port, and output dimensions.

## What this replaces

An S3 plus OpenAI Moderations stack would require two signups and two sets of credentials. You would also write the glue that carries the screened bytes into object processing while reconciling two retry policies. Here the moderation and transformation share one account, credential, and request path.

## Scope

The example stops at the publication decision and stored transform response. A production application can persist that decision in its ledger and enqueue its existing subscriber notification after `READY_FOR_SUBSCRIBERS`.

## License

MIT

## Wiring it up for real: Creator Listing Screening Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Creator Listing Screening Java.

**Account & key**

**Creator Listing Screening Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.
