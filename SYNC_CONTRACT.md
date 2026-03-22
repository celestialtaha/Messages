# Phone-Watch Sync Contract (v1)

Shared with `Wessage` Wear app.

## Schema Version

- `SYNC_SCHEMA_VERSION = 1`

## Data Paths

- `/wessage/sync/v1/conversations`
- `/wessage/sync/v1/messages`
- `/wessage/sync/v1/mutation`
- `/wessage/sync/v1/ack`
- `/wessage/sync/v1/bootstrap_request`
- `/wessage/sync/v1/key_exchange/request`
- `/wessage/sync/v1/key_exchange/response`

## Payload Types

- `ConversationDeltaBatch`
- `MessageDeltaBatch`
- `WatchMutation`
- `MutationAck`
- `BootstrapRequest` (`limit`, `offset`)

## Notes

- Phone remains authoritative.
- Watch actions must include `clientMutationId`.
- Watch may request paged bootstrap via `BootstrapRequest`.
- Every payload carries `schemaVersion`.
- Key exchange payloads carry the sender P-256 public key.
- Sync payloads on `conversations`, `messages`, `mutation`, and `ack` are AES-256-GCM envelopes derived from ECDH.
- Encrypted envelopes include counters and must be replay-protected.
