# API Reference — Hash-only Threat Intel

All endpoints are content-free: no SMS body or phone number is ever transmitted.

## Detection Service (`:8081`)

| Method | Path | Body / Params | Purpose |
|---|---|---|---|
| POST | `/api/v1/detection/classify` | `ClassificationRequest` | Classify a message hash |
| POST | `/api/v1/detection/feedback` | `FeedbackRequest` | Report a corrected verdict |
| GET | `/api/v1/detection/signatures` | — | Top reported threat hashes |
| GET | `/api/v1/detection/stats` | — | Signature count + version |
| GET | `/actuator/health` | — | Liveness/readiness |

### ClassificationRequest
```json
{
  "messageHash": "sha256-hex-of-message-body",
  "senderHash": "sha256-hex-of-sender-id",
  "clientRuleVersion": "7",
  "locale": "en"
}
```

### ClassificationResponse
```json
{
  "messageHash": "...",
  "verdict": "SAFE | SUSPICIOUS | SPAM | PHISHING",
  "spamScore": 0.95,
  "category": "PHISHING",
  "matchedRuleId": "hash-prefix-phishing",
  "signatureVersion": 7
}
```

## Reputation Service (`:8082`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/reputation/{senderHash}` | Look up a sender's reputation |
| POST | `/api/v1/reputation/batch` | `{ "hashes": ["..."] }` |
| POST | `/api/v1/reputation/report` | `{ "senderHash": "...", "label": "BLOCKED", "score": 95 }` |

## Privacy notes
- `messageHash` and `senderHash` are one-way SHA-256 values. They cannot be reversed into message content or phone numbers.
- Feedback stores only the hash + label, never raw text.
- On-device rules in `DetectionEngine.kt` handle all message bodies locally.
