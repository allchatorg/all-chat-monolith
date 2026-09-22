# allchat Pro font presets

Apply `docs/sql/pro-fonts.sql` before deploying the backend with production schema validation. Deploy the backend before the frontend; added response fields are backward compatible.

`GET` and `PATCH /api/v1/settings/fonts` return `usernameFont`, `messageFont`, `fontRevision`, `proActive`, `dailyLimit`, `changesRemaining`, and an ISO UTC `resetsAt`. PATCH requires both preset IDs: `DEFAULT`, `INTER`, `OPEN_SANS`, `NUNITO`, `COMFORTAA`, or `CAVEAT`. One successful changed pair uses one of five daily saves; retries of the current pair are free. The allowance resets at midnight UTC.

Nunito adds a soft rounded option, Comfortaa a geometric rounded option, and Caveat a handwritten option. All presets are available for both usernames and messages. The frontend bundles the fonts and licenses locally, with Latin and Cyrillic coverage. These additional IDs fit the existing `varchar(16)` columns and require no further migration after `pro-fonts.sql`. Deploy the expanded backend enum before the matching frontend so new selections can be saved.

Expired entitlement immediately renders default fonts. The existing expiry sweep clears saved custom choices, including when the badge is hidden. Billing also clears expired choices before granting a renewed entitlement. A delayed renewal after local `proPaidThrough` has elapsed counts as expiry; choices do not return automatically. Automatic resets increment `fontRevision` and leave the allowance unchanged.

Messages resolve the author's current profile fonts, including historical messages and replies. Font saves do not broadcast globally. Public user/message responses and the existing badge lookup/events carry the effective font pair and its independent revision. Clients must merge the font tuple independently of the badge revision. At the same font revision, a complete default pair takes precedence over a stale custom pair; higher revisions take precedence over lower ones.

The normal Maven tests cover service, controller, lifecycle, and mapper behavior. `ProFontPostgresTest` additionally checks real row locking, concurrent final-quota saves, stale ordinary user saves, hidden-badge expiry, and migration idempotency. It is opt-in and creates/drops tables only in a disposable localhost database named `font_test`, using the `postgres` user and the test password `font_test`:

```sh
docker run --detach --rm --name allchat-font-presets-test --tmpfs /var/lib/postgresql/data -e POSTGRES_PASSWORD=font_test -e POSTGRES_DB=font_test -p 127.0.0.1::5432 postgres:17-alpine
docker port allchat-font-presets-test 5432/tcp
# Substitute the printed port in the next command.
./mvnw -pl chat -am test -Dfont.test.database.url=jdbc:postgresql://127.0.0.1:PORT/font_test
docker stop allchat-font-presets-test
```

If the local JVM cannot attach Mockito automatically, pass its locally installed `mockito-core` JAR using `-DargLine=-javaagent:/absolute/path/to/mockito-core.jar`.
