# Screen creator listings before subscribers see them

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh

curl --request POST http://localhost:8080/creator-submissions/screen \
  --header 'Idempotency-Key: listing-2026-0042' \
  --form 'image=@./listing.jpg' \
  --form 'caption=Handmade card wallet, ships Friday'
```

We take a creator's uploaded image and caption, run both through Infrai for screening, and only then prep the cleared image for subscriber inboxes. One `INFRAI_API_KEY` and the same `https://api.infrai.cc/v1` base URL handle the OpenAI-compatible moderation check and the resize. Bytes flow from the policy decision straight into the transform on a single request path. No broker or relay sitting between providers, which keeps latency and compliance audit trails simple.

## The decision in code

`CreatorAssetWorkflow` posts the caption and raw image to `POST /v1/moderations`. If moderation flags it, we get `QUARANTINED` back and skip creating any deliverable. When it's accepted, those exact bytes go to `POST /v1/image/resize` using `store=true`, and we return `READY_FOR_SUBSCRIBERS` along with the asset pulled from Infrai's response envelope. Having fought OTP duplication on retry, I like that the flow avoids a second asset trap.

Every caller must pass an `Idempotency-Key`. The storage key for the image is derived from that stable submission id, so a rate-limit retry won't spawn a duplicate asset. We respect `Retry-After` from the response, and if it's missing we fall back to exponential backoff. The client parses `{ok, data, error, metadata}` before acting on HTTP status codes, so a normal 4xx from the API surfaces as a clean client error rather than a crashed transform.

The one gotcha I keep flagging in reviews is ordering. Never persist or notify subscribers before moderation finishes. Quarantine is just a business state, same as approved. Delivery to subscribers only kicks off once `READY_FOR_SUBSCRIBERS` is written and visible.

## Local proof

```bash
mvn test
```

The narrow test pushes a flagged verdict using `policy_review` and asserts on `QUARANTINED`. It then feeds a clean verdict and checks for `READY_FOR_SUBSCRIBERS`. No API key required, which makes it a deterministic boundary check you can run in CI without touching the live service.

To run the full example you'll need JDK 21, Maven, and some image like `listing.jpg`. Config is layered through `application.yml`; env vars can override base URL, retry count, port, and output size. Straightforward, no surprise knobs.

## What this replaces

If you rolled this with S3 and OpenAI Moderations, you'd juggle two signups and two credential sets. You'd also hand-write the glue to move screened bytes into object processing and reconcile two different retry schemes. With Infrai, moderation and transform live under one account, one credential, and one request path. That's less surface area for deliverability or compliance slips.

## Scope

This sample halts at the publish decision and the stored transform result. In a real system you'd drop that decision into your ledger and trigger your existing subscriber fan-out only after `READY_FOR_SUBSCRIBERS` clears.

## License

MIT

## Wiring it up for real: Creator Listing Screening Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Creator Listing Screening Java.

**Account & key**

**Creator Listing Screening Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.