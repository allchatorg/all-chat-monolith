# allchat Pro stickers and inline emojis

The existing message endpoint accepts an optional `stickerId` containing one of
the 17 short IDs in `ProCharacterCatalog`, such as `pepe`. A sticker is a
standalone message: send empty or omitted `content`, no attachments, and an
optional `replyToMessageId`. Public and private messages use the same validation
and existing access rules. Current allchat Pro entitlement is checked in the
database; badge visibility does not grant or remove access. Unknown IDs, URLs,
mixed text/sticker content, and free or expired subscriptions are rejected.

Message responses and reply previews include nullable `stickerId`. Clients
resolve images from their local catalog. Reading existing stickers does not
require Pro. Deleted sticker IDs are omitted from ordinary responses and
deletion broadcasts; staff history/search can retain them. Quarantined sticker
IDs and redacted reply IDs are always hidden. The existing character reaction
contract (`allchat:<id>`) is unchanged and shares the same allowlist.

Standalone stickers cannot be edited or promoted. Consequently they do not
create edit-history snapshots; ordinary message histories retain null
`stickerId`. Clients must hide those unsupported actions as well.

## Inline emojis

Inline emojis use the existing `content` field with canonical markers such as
`:allchat:pepe:`. The IDs are the same 17 entries in `ProCharacterCatalog`;
URLs are never accepted as emoji identities. Markers inside HTTP(S) URLs stay
literal URL text, and identities split by formatting markers are not emojis.
Clients resolve images from their local catalog and render unknown identities
as unavailable emojis.

The common send and edit paths validate catalog identity and current allchat
Pro entitlement before persistence. New messages require Pro for any inline
emoji. An edit can retain, move, or remove already-sent emojis even after Pro
expires, but increasing the count of any identity requires current Pro access.
Unknown IDs cannot be added; an old unknown identity may be retained or removed
without increasing its count. Existing messages and edit histories remain
readable without a subscription, subject to the usual deletion and quarantine
redaction. Custom reactions continue to use `allchat:<id>` and remain removable
after Pro expires.

Chat counts each complete inline marker as one UTF-16 character toward the
500-character visible limit. The separate 2,000-character raw limit still
applies. `content_plain` stores one U+FFFC placeholder per emoji to fit the
existing 500-character column. Advertising parsing, limits, and pricing keep
their existing `MessageMarkers` behavior. Inline emojis require no new column,
migration, or billing configuration.

## Deployment

Apply `docs/sql/sticker-messages.sql` to an existing PostgreSQL database before
deploying this backend, then deploy the matching frontend. Production uses
Hibernate schema validation; the SQL migration is deliberately manual and
adds only the nullable `messages.sticker_id` column. Existing rows require no
backfill. No billing configuration changes are needed. The additive column
can remain in place during rollback, preserving any already-sent stickers.
Deploy the backend inline-emoji validation before enabling the matching
frontend; older frontends may display canonical inline markers as text.

## Verification

Compile with `./mvnw -pl chat,ads -am -Dmaven.test.skip=true compile` and review
the shared send/edit validation, visible/raw length handling, URL exclusion,
redaction, and advertising isolation. No additional tests or manual site
interactions are part of this change.
