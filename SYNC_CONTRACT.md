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

## Payload Types

- `ConversationDeltaBatch`
- `MessageDeltaBatch`
- `WatchMutation`
- `MutationAck`

## Notes

- Phone remains authoritative.
- Watch actions must include `clientMutationId`.
- Every payload carries `schemaVersion`.
