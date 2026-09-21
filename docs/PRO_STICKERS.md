# allchat Pro stickers

Active allchat Pro subscribers can send one catalog sticker with a message in
public rooms and in private conversations they can already access. Everyone can
view stickers already sent. Existing room permissions, moderation, text limits,
and attachment checks continue to apply.

## API contract

`POST /api/v1/chatting/messages` accepts an optional `stickerId` alongside the
existing fields:

```json
{
  "chatRoomId": 5,
  "content": "",
  "attachments": [],
  "replyToMessageId": null,
  "stickerId": "pepe"
}
```

Text and attachments may accompany a sticker. For sticker-only messages, omitted
or null `content` becomes an empty string; omitted or null `attachments` is also
accepted. A message without text, attachments, or a sticker is rejected.

Supported IDs are `wojak`, `soyjak`, `chud`, `chad-1`, `chad-2`, `virgin`,
`doomer`, `coomer`, `bloomer`, `zoomer`, `npc`, `grug`, `pepe`, `apu-apustaja`,
`honkler`, `spurdo`, and `gondola`. IDs are exact and case-sensitive; arbitrary
image URLs and unknown IDs return HTTP 400.

Each sticker send reads the current billing projection directly from the
database. Paid-through must be in the future, and the account must be neither
deleted nor banned. Missing or expired entitlement returns HTTP 403. Badge
visibility and cached client/profile flags do not grant or remove entitlement.

Message HTTP responses, WebSocket events, paginated history, edit history, and
reply previews include the nullable `stickerId`. Reply previews hide it whenever
the existing moderation rules hide the parent content. The frontend resolves
catalog IDs to its bundled transparent assets.

The ads portal's promotion detail and listing responses expose the same catalog
value as `messageStickerId`, allowing owners and moderators to review promoted
sticker-only messages. Report cases, deletion audit records, and conversation
previews inherit `stickerId` through their embedded message response.

Editing changes the caption and preserves the original sticker; it cannot add or
replace a sticker. A sender may edit an existing caption after Pro expires.
Previous captions and sticker IDs are archived together. Removing the last
attachment remains valid when a sticker is present. Deleted or quarantined
messages cannot be edited.

## Deployment

For an existing PostgreSQL installation, apply `docs/sql/pro-stickers.sql` after
the existing `docs/sql/allchat-pro.sql` migration and before starting this backend
with schema validation enabled. It adds nullable `sticker_id varchar(32)` columns
to `messages` and `message_edit_history`; old messages remain unchanged. The SQL
is idempotent. Deploy the backend before enabling the matching frontend picker.

Development schemas created by Hibernate pick up the new columns automatically.
The feature uses the existing Pro billing configuration and needs no new secrets.

## Verification

Run chat tests and compile the full backend:

```sh
./mvnw -pl chat,ads -am test
./mvnw -DskipTests compile
```

If the local JDK cannot attach Mockito dynamically, pass its startup agent using
the version managed by this project's Spring Boot dependencies:

```sh
./mvnw -pl chat,ads -am test \
  -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar"
```

Coverage includes all 17 IDs, hidden badges, free and expired subscriptions,
stale cached entitlement, both room types, invalid IDs, caption editing,
attachment removal, edit snapshots, generated DTO mappings, and reply redaction.
Promotion tests verify owner/admin detail access, list previews, JSON field names,
and compatibility with ordinary messages that have no sticker.
