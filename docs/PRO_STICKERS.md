# allchat Pro character reactions

allchat Pro includes **17 exclusive character reactions** attached to messages.
The reaction picker has **Emoji** and **allchat Pro** tabs, with search, character
previews and locked states. Everyone can see the character images, counts and
reacting users. An active subscriber can add a character reaction or click an
existing one; anyone can remove their own reaction after their subscription ends.
The existing transparent artwork is bundled with the frontend.

## API contract

The existing `PATCH /api/v1/chat-rooms/message-reactions` (add) and
`DELETE /api/v1/chat-rooms/message-reactions` (remove) endpoints accept the same
request fields. A character uses the same reserved token in both identity fields:

```json
{
  "messageId": 123,
  "emoji": "allchat:pepe",
  "emojiId": "allchat:pepe"
}
```

Supported suffixes are `wojak`, `soyjak`, `chud`, `chad-1`, `chad-2`, `virgin`,
`doomer`, `coomer`, `bloomer`, `zoomer`, `npc`, `grug`, `pepe`, `apu-apustaja`,
`honkler`, `spurdo`, and `gondola`. If either field starts with `allchat:`, both
must be exactly equal and identify a known character; otherwise HTTP 400 is
returned. Unicode emoji keep their existing identities and do not require Pro.

Every custom addition checks the current billing projection in the database:
paid-through must be in the future and the account must be neither deleted nor
banned. Missing or expired entitlement returns HTTP 403. Hidden badges and stale
client/session flags do not affect entitlement. Removal validates the identity
but never requires active Pro.

`GET /api/v1/chat-rooms/messages/{messageId}/reactions/{emoji}?limit=5` accepts a
URI-encoded identity, e.g. `allchat%3Apepe`. The optional nonnegative limit applies
only to returned users; `usersCount` remains the full count and persisted
membership is never changed by a detail read. Existing message summaries and
reaction WebSocket payloads carry both tokens unchanged. The frontend resolves
only its known local catalog entries to image assets.

## Permissions and consistency

Mutation and detail reads validate public room access or private conversation
membership and reject deleted/quarantined messages. Archived rooms reject
mutations. Private additions also preserve the existing staff-only and blocked
conversation write restrictions; members can still read or remove reactions.

Both add and remove lock the message row in the database before inspecting
reaction membership, including the first addition when a reaction row does not
exist. Repeated requests are idempotent, and activity counters/socket events are
updated only when membership actually changes. Removed users are matched by ID.

Activity and WebSocket updates run after a successful commit, with recipient IDs
and payloads captured inside the transaction. Private updates use member queues;
public updates use the room topic. Delivery failures are logged without undoing
saved reactions. Redis/socket delivery remains best effort: retries and ordering
across concurrent commits require an outbox/versioned event design beyond the
existing API. Reloading messages uses the authoritative database state.

## Deployment and verification

Reaction support uses existing database fields and Pro billing setup. Hibernate
creates the entity schema under the disposable configuration described in
[allchat Pro operations](allchat-pro.md#database-deployment).

Run the complete reactor tests and compile:

```sh
./mvnw test
./mvnw -DskipTests compile
```

If the JDK cannot attach Mockito dynamically, append
`-DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"`
to the test command.

Coverage includes all 17 identities, Unicode emoji, free/expired/hidden-badge
entitlement, stale cached state, invalid tokens, access checks, private delivery,
idempotency and edit safety. H2/JPA tests exercise concurrent first additions,
duplicate removals, add/remove cycles, membership persistence after limited
previews, current database entitlement, transaction rollback and delivery failure.

The database integration suite runs against H2; it does not replace a production
PostgreSQL concurrency/load check.
