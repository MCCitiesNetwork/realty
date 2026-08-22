# PlayerNotifications adapter for Realty

Date: 2026-08-22

## Goal

Deliver Realty notifications through the PlayerNotifications (PN) plugin, so players get
per-category preferences, sink fan-out, offline delivery and an inbox — instead of the
all-or-nothing online-chat delivery `chat-adapter` provides.

## 1. Event change: carry the message key

`RealtyNotificationEvent` (realty-paper-api) gains `@NotNull String messageKey`, placed
second:

```java
public RealtyNotificationEvent(@NotNull List<UUID> targets,
                               @NotNull String messageKey,
                               @NotNull Component message,
                               @Nullable WorldGuardRegion region)
```

`messageKey` is the `messages.yml` path the fire site rendered from, e.g.
`"notification.outbid"`. It is an identity for routing/filtering, never rendered.
Consumers MUST tolerate unknown keys.

- Validated non-blank in the constructor, alongside the existing empty-targets check.
- Stays a plain `String`: `MessageKeys` lives in `realty-paper`, the event in
  `realty-paper-api`. No dependency inversion; third-party fire sites may use own keys.
- Position 2 (not trailing) so every un-migrated call site fails to compile.

### Fire-site migration — 33 sites, 9 files

Mechanical: the constant is already the first argument to the adjacent `messageFor` call.

```java
events.fireSync(new RealtyNotificationEvent(List.of(bidderId),
        MessageKeys.NOTIFICATION_OUTBID,
        this.messages.messageFor(MessageKeys.NOTIFICATION_OUTBID, ...),
        region));
```

| File | sites |
|---|---|
| `listener/RegionNotificationListener.java` | 15 |
| `command/OfferCommandGroup.java` | 6 |
| `Realty.java` (expiry sweeps, ~L402-441) | 4 |
| `command/AuctionCommandGroup.java` | 3 |
| `command/AgentInvite{Accept,Reject,Withdraw}Command.java`, `AgentInviteCommand.java`, `AgentRemoveCommand.java` | 1 each |

Repeating the constant is deliberate; reworking `MessageContainer` to return a
(key, component) pair would touch far more than these 33 sites for a cosmetic gain.

Existing consumers `ChatNotificationListener` and `EssentialsMailListener` ignore the new
field — no behaviour change. Their tests and `RealtyNotificationEventTest` need the new
argument threaded through.

## 2. The adapter module

New subproject `realty-paper-adapters/player-notifications-adapter`, added to `settings.gradle.kts`,
mirroring `essentials-adapter`: `java-library` + `realty-conventions` + shadow, everything
`compileOnly` (`realty-paper`, `realty-paper-api`, paper-api, annotations,
`plugin-infrastructure`) plus:

```kotlin
compileOnly("io.github.md5sha256:player-notifications-api:1.0.0")
```

Available from the `maven.democracycraft.net` repos Realty already declares — no
build-script repo changes.

`module-manifest.yml`: `module-name: player-notifications-adapter`, `reloadable: true`.

`PlayerNotificationsAdapterModule extends SimplePluginModule<Realty>`, initialize order —
**all fallible work before `registerListener`**, because if anything after it throws,
`ModuleLifecycleManager` closes the class loader without calling `shutdown()`, leaving a
live listener on a dead class loader:

1. Look up `NotificationService` from Bukkit's services manager (PN registers it in
   `PlayerNotificationsPlugin#onEnable`); throw `IllegalStateException` if PN is absent or
   disabled.
2. Load the key -> dataType mapping from the module's `dataFolder`.
3. Register payload types, renderers, categories.
4. `registerListener(...)`.

`shutdown` unregisters the listener and all five dataTypes (see the trap in §3).

`PlayerNotifications` goes in Realty's `paper-plugin.yml` as a **softdepend with
`join-classpath: true`** — exactly how EssentialsX is handled. Realty must not hard-depend
on a plugin optional for most servers; the module fails loudly at initialize instead.

## 3. Payload, renderer, categories

Registries are plain `HashMap.put` (`NotificationDataTypeRegistry`), so re-registration is
idempotent and `reloadable: true` is safe.

**One payload class, five dataTypes.** The registry keys serializers/renderers by payload
*class* and `payloadMapping` by dataType. Sharing one class across our dataTypes means
sharing handlers — which is exactly what we want, since only the routing label differs.

```java
public record RealtyNotificationPayload(
        @NotNull String messageKey,   // provenance + title source
        @NotNull String body,         // the Component, GSON-serialized
        @Nullable String regionId,    // null is routine: refund after region deletion
        @Nullable String worldId) { }
```

`body` uses `GsonComponentSerializer`, not MiniMessage: the event hands us a built
`Component` and a MiniMessage round-trip is lossy for programmatically-built components.
Region identity is carried as strings because the payload is persisted and outlives the
region.

`NotificationRenderer<RealtyNotificationPayload>` returns `RenderableNotification(title,
body)` with the body deserialized verbatim. Titles come from module config, keyed by
category with a per-messageKey override. Titles MUST NOT rely on click events — non-
Minecraft sinks flatten to plain text.

Registered via `registerJsonRenderable` (never `registerJsonPayload`: an explicit processor
wins dispatch precedence and bypasses preferences and sinks entirely), each claimed under a
matching category in `categoryRegistry`:

| dataType | covers |
|---|---|
| `realty.auction` | outbid, auction won / ended-no-bids / cancelled, bid payment expiry |
| `realty.offer` | offer placed / accepted / rejected / withdrawn, offer payment expiry |
| `realty.lease` | leasehold expiry & termination, modification proposals & resolutions, rented / unrented |
| `realty.agent` | the five agent-invite and agent-removal notifications |
| `realty.general` | region bought, ownership transferred, **and the fallback for unmapped keys** |

The mapping ships as `categories.yml` in the module data folder, defaulted to the table
above. An unrecognised key routes to `realty.general` with a `FINE` log — never dropped.

### The shutdown trap

All five dataTypes share one payload class, so `unregisterPayloadMapping("realty.auction")`
cascades into `unregisterSerializer`/`unregisterRenderer` on the shared class, silently
breaking the other four. `shutdown` therefore unregisters **all five**; doing it partially
is what corrupts state. This is a footgun in the PN API and must be commented as such.

### Enqueue

One `TypedNotification<RealtyNotificationPayload>` per event:

- `notifKey` — a fresh `UUID`
- `notifScheduledTime` — `Instant.now()`
- `notifExpiryTime` — from config, default 30 days
- `notifTarget` — the event's target list verbatim
- `notifPriority` — from config, per category
- `overwriteAllowed` — `false`; Realty notifications are distinct events, never updates

## 4. De-bundling chat-adapter

The `chat-adapter` subproject is unchanged — it keeps building, testing and shadow-jarring.

1. **`realty-paper/build.gradle.kts`** — remove
   `dependsOn(":realty-paper-adapters:chat-adapter:shadowJar")` and the
   `from(...) { into("modules") }` block from `shadowJar` (~L161-166).
2. **`Realty.startModules()`** — remove the `BundledModuleExtractor.extract(...)` call and
   its `IOException` handler. `BundledModuleExtractor` then has no callers: delete it.
   - Keep both warnings, reworded from failed-extraction recovery advice to a plain
     statement that no delivery module is installed and where to put one.
   - The "no modules at all" warning names `player-notifications-adapter.jar` alongside `chat-adapter.jar`.
   - The chat-adapter-specific warning stays quiet when `player-notifications-adapter` is loaded, so a
     PN-only server does not warn at every startup about a deliberate choice.
3. **`runServer`** — keep staging `chat-adapter.jar` (a dev convenience independent of
   shipping; removing it breaks local smoke tests) and add `player-notifications-adapter.jar` to the same
   `doFirst` staging. PN itself is not downloaded by `runServer`, so `player-notifications-adapter` will fail
   there with the clear "PlayerNotifications is not installed" error. That is correct.

**Upgrade behaviour:** an existing server keeps its previously-extracted
`chat-adapter.jar`; nothing deletes it, so behaviour is unchanged across the upgrade and
the jar is now under operator control. No cleanup pass (explicit decision).

**New-install behaviour change:** a fresh install delivers nothing until the operator
installs a module. Covered by the startup warning, which stays at `WARNING`.

`README.md` and `CLAUDE.md` both currently state chat-adapter is bundled and extracted on
first enable; both must change to "published, install it yourself".

## 5. Testing

Follow the existing adapter test shape: constructor-injected functional interfaces, plain
JUnit 5, no MockBukkit, no Bukkit runtime.

- **`RealtyNotificationEventTest`** — thread `messageKey` through existing cases; add
  blank/null key rejection, matching the existing empty-targets test.
- **`RealtyNotificationPayloadTest`** — `GsonComponentSerializer` round-trip: a coloured
  component, a programmatically-built one with hover/click, one with null region/world.
  This is the executable form of the "MiniMessage would be lossy" decision.
- **`NotificationCategoryMapperTest`** — the mapper is extracted as a standalone class
  precisely so this needs no PN: each category resolves from a representative key; an
  unmapped key falls back to `realty.general`; a config override beats the default.
- **`PlayerNotificationsListenerTest`** — recording fake service: one event enqueues
  exactly one `TypedNotification`; targets carried verbatim incl. multi-target;
  `overwriteAllowed` is false; two events from the same key get different `notifKey`s; a
  null region yields null `regionId`/`worldId` without throwing.
- **Registration lifecycle** — against a real `NotificationDataTypeRegistry` (plain
  concrete class, no Bukkit): registering then unregistering all five leaves it clean, and
  the partial-unregister hazard is asserted so the footgun is documented executably.
- **Existing adapter tests** — thread the new constructor argument through; no behavioural
  assertions change.

**Not automated** (manual `runServer` checklist): the services-manager lookup, module load
ordering against PN's `onEnable`, and the de-bundling.
