# Event-driven notifications and the Essentials adapter module

Date: 2026-08-21
Status: approved for planning

## Problem

`NotificationService` (`realty-paper-api/.../api/NotificationService.java`) is a four-overload
interface with two implementations picked at enable time in `Realty.java:262-270`:

- `TransientNotificationService` — `Bukkit.getPlayer(uuid)`, send if online, silently drop otherwise.
- `EssentialsNotificationService` — flattens the `Component` through `LegacyComponentSerializer`
  into Essentials mail.

Twenty-one call sites across `command/*` and `Realty.scheduleTasks()` build a `Component` from
`MessageContainer` and push it straight into the service. Two consequences:

1. **Delivery is closed.** Nothing outside `realty-paper` can observe that a region was sold, a
   bidder was outbid, or a lease expired. Adding a delivery medium means editing core.
2. **Essentials is welded into core.** `realty-paper` carries a `compileOnly` EssentialsX
   dependency and `com.earth2me` imports for two unrelated concerns — mail delivery
   (`EssentialsNotificationService`) and teleport safety (`EssentialsSafeBlockPredicate`).

## Goals

- Realty core fires events and delivers nothing. Delivery is entirely a listener concern.
- Essentials support lives in its own module jar, loaded by the existing
  `ModuleLifecycleManager<Realty>`; core holds no `com.earth2me` imports.
- Default installs behave as they do today (online players get chat) without core doing delivery.

## Non-goals

- Typed JSON payloads and the `player-notifications` plugin backend. That direction is abandoned;
  `memory-bank/player-notifications-integration-plan.md` is deleted as part of this work.
- Changing `messages.yml`, any `MessageKeys.NOTIFICATION_*` constant, or the wording of any
  notification. Message construction moves, verbatim, from call site to event constructor argument.
- Persistent/store-and-forward notifications. An offline target with no adapter installed still
  receives nothing; that is the adapters' problem to solve, not core's.

## Decisions

| Question | Decision |
|---|---|
| Event mechanism | Bukkit events. Third parties and modules listen with no realty-side wiring. |
| Threading | Async events (`super(true)`). Listeners own their main-thread marshalling. |
| Event payload | Pre-rendered `Component`, **plus** the domain fields the call site already holds. |
| Adapter packaging | `plugin-infrastructure` module jars under `realty-paper-adapters/`. |
| No-adapter behaviour | A bundled `chat-adapter` module preserves today's online-player baseline. |
| SafeLocationFinder | Only the predicate moves; the finder and `TeleportCommand` stay in core. |

Two points were assumed rather than confirmed and are called out here so they are easy to reverse:

- **Domain fields alongside the `Component`.** The brainstorm settled on a pre-rendered `Component`;
  this spec additionally keeps each event's domain fields (price, actor id and name, duration).
  The call sites already hold these values, so the cost is one or two extra constructor arguments,
  and it is the difference between an event a third party can act on and one it can only re-print.
  The `Component` remains authoritative for delivery.
- **`chat-adapter` is bundled inside the main jar** and extracted on first enable. The alternative
  is shipping it as a separate download users must drop in; that leaves a bare install silent.

## Architecture

### 1. Event layer — `realty-paper-api`, package `io.github.md5sha256.realty.api.event`

```java
public abstract class RealtyNotificationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<UUID> targetIds;
    private final Component message;
    private final String regionId;
    private final UUID worldId;

    protected RealtyNotificationEvent(@NotNull List<UUID> targetIds,
                                      @NotNull Component message,
                                      @NotNull String regionId,
                                      @NotNull UUID worldId) {
        super(true); // async: fired from CompletableFuture callbacks and the async sweep task
        // ...
    }

    public @NotNull List<UUID> targetIds() { /* ... */ }
    public @NotNull Component message() { /* ... */ }
    // regionId(), worldId(), getHandlers(), getHandlerList()
}
```

A single-target convenience constructor wraps one `UUID` in a `List.of(...)`. The list form exists
for the reject-all fan-out at `OfferCommandGroup.java:490`, which currently loops one enqueue per
offerer.

One concrete subclass per existing `MessageKeys.NOTIFICATION_*` constant, so a listener can filter
on something meaningful rather than string-matching a message:

| Event | Message key | Extra domain fields |
|---|---|---|
| `RegionBoughtEvent` | `notification.region-bought` | `buyerId`, `buyerName`, `price` |
| `OwnershipTransferredEvent` | `notification.ownership-transferred` | `newHolderId`, `newHolderName` |
| `RegionRentedEvent` | `notification.region-rented` | `tenantId`, `tenantName`, `price`, `durationSeconds` |
| `RegionUnrentedEvent` | `notification.region-unrented` | `tenantId`, `tenantName`, `refund` |
| `LeaseholdExpiredEvent` | `notification.leasehold-expired` | `tenantId`, `landlordId` |
| `LeaseholdExpiredLandlordEvent` | `notification.leasehold-expired-landlord` | `tenantId`, `landlordId` |
| `OutbidEvent` | `notification.outbid` | `newBidderId`, `newBidAmount` |
| `AuctionWonEvent` | `notification.auction-won` | `winnerId`, `winningBid` |
| `AuctionEndedNoBidsEvent` | `notification.auction-ended-no-bids` | `auctioneerId` |
| `AuctionCancelledEvent` | `notification.auction-cancelled` | `auctioneerId` |
| `BidPaymentExpiredEvent` | `notification.bid-payment-expired` | `bidderId`, `refundAmount` |
| `OfferPlacedEvent` | `notification.offer-placed` | `offererId`, `offererName`, `price` |
| `OfferAcceptedEvent` | `notification.offer-accepted` | `offererId`, `price` |
| `OfferRejectedEvent` | `notification.offer-rejected` | `offererId` |
| `OfferWithdrawnEvent` | `notification.offer-withdrawn` | `offererId` |
| `OfferPaymentExpiredEvent` | `notification.offer-payment-expired` | `offererId`, `refundAmount` |
| `AgentInvitedEvent` | `notification.agent-invited` | `inviterId`, `inviteeId` |
| `AgentInviteAcceptedEvent` | `notification.agent-invite-accepted` | `inviterId`, `inviteeId` |
| `AgentInviteRejectedEvent` | `notification.agent-invite-rejected` | `inviterId`, `inviteeId` |
| `AgentInviteWithdrawnEvent` | `notification.agent-invite-withdrawn` | `inviterId`, `inviteeId` |
| `AgentRemovedEvent` | `notification.agent-removed` | `removerId`, `targetId` |

Agent events are region-scoped in every current call site, so they carry `regionId`/`worldId` like
the rest; where a call site genuinely lacks a world, that is a signal the event is misplaced and
should be caught in review rather than papered over with a nullable field.

### 2. Deletions and call-site changes

Deleted outright:

- `realty-paper-api/.../api/NotificationService.java`
- `realty-paper/.../util/TransientNotificationService.java`
- `realty-paper/.../util/EssentialsNotificationService.java`
- `realty-paper/.../util/EssentialsSafeBlockPredicate.java` (moves to the module)
- `memory-bank/player-notifications-integration-plan.md`

Each of the twenty-one call sites drops its `NotificationService` constructor parameter and
replaces

```java
notificationService.queueNotification(success.previousTitleHolderId(), component);
```

with

```java
Bukkit.getPluginManager().callEvent(
        new RegionBoughtEvent(success.previousTitleHolderId(), component,
                regionId, worldId, buyerId, buyerName, success.price()));
```

The `MessageContainer.messageFor(...)` call that builds `component` stays exactly where it is.
`Realty.java` loses the `notificationService` field (`:142`), the Essentials branch (`:262-270`),
and the parameter threading through `registerCommands` (`:620-653`).

**Threading constraint.** Bukkit throws `IllegalStateException` from
`PluginManager.callEvent` when an *asynchronous* event is fired from the primary server thread. An
async event fired on the main thread is **not** valid. This bites in more places than it looks:
`RealtyPaperApiImpl.buy()`, `rent()`, `unrent()`, `payBid()` and `payOffer()` all terminate in
`.thenComposeAsync(..., executorState.mainThreadExec())`, so a plain `.thenAccept(...)` in the
calling command runs on the *main* thread; and leasehold expiry fires from inside a
`scheduler.runTask` lambda, which is the main thread by definition.

Rather than a thread check at each of the twenty-three fire sites, every event is fired through
`io.github.md5sha256.realty.NotificationDispatcher.fire(event)` in `realty-paper`. It calls
`PluginManager.callEvent` directly when already off the primary thread, and otherwise hops onto
`ExecutorState.networkExec()` — a thread-per-task executor whose `ThreadFactory` installs the plugin
class loader as the thread context class loader, which module classes loaded by the module
`URLClassLoader` depend on. `dbExec()` is deliberately not used: it is a fixed pool of four and
blocking it on listener work risks starving database calls.

The dispatcher holds its executor in a `static volatile` field, written only by
`Realty.onEnable` (before commands are registered) and cleared by `onDisable`; the fire sites are
`record` command beans that hold neither the plugin nor its `ExecutorState`, and threading a new
constructor parameter through all of them buys nothing over a contained single-writer static. A fire
that finds no executor — an aborted enable, or a race with shutdown — is dropped with a warning
rather than throwing.

Leasehold expiry keeps its `scheduler.runTask` hop for the WorldGuard work and fires its events from
inside it, since the alternative would reorder flag application against notification; the dispatcher
makes that safe.

### 3. `realty-paper-adapters/chat-adapter`

A `SimplePluginModule<Realty>` with one listener on the base `RealtyNotificationEvent`, so a single
handler covers every subclass:

```java
@EventHandler(priority = EventPriority.NORMAL)
public void onNotification(RealtyNotificationEvent event) {
    Component message = event.message();
    List<UUID> targets = event.targetIds();
    plugin().executorState().mainThreadExec().execute(() -> {
        for (UUID target : targets) {
            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    });
}
```

This is `TransientNotificationService`'s behaviour, relocated. It is deliberately unconditional:
the Essentials adapter mails *in addition* rather than instead, matching how a player who is online
today sees the chat message and nothing else, while an offline player gets mail.

The built jar is embedded as a resource in `realty-paper`'s shadowJar and extracted to
`plugins/Realty/modules/chat-adapter.jar` during `onEnable` if that path does not already exist —
never overwriting, so an operator who removes or replaces it keeps their choice across restarts.
`runServer` copies it in the same way.

Manifest (`module.yml`):

```yaml
moduleName: chat-adapter
entryClass: io.github.md5sha256.realty.adapter.chat.ChatAdapterModule
author: md5sha256
expectedPluginClass: io.github.md5sha256.realty.Realty
reloadable: true
```

### 4. `realty-paper-adapters/essentials-adapter`

A `SimplePluginModule<Realty>` containing:

- **`EssentialsMailListener`** — the body of `EssentialsNotificationService`, as an
  `@EventHandler(priority = EventPriority.HIGH)` on `RealtyNotificationEvent`. For each target it
  resolves `essentials.getUser(uuid)`, logs a warning and skips on null, and calls
  `essentials.getMail().sendMail(user, Console.getInstance(), legacy)` where `legacy` is
  `LegacyComponentSerializer.legacySection().serialize(event.message())`. Mail is sent only when
  `Bukkit.getPlayer(target) == null`, so an online player is not both chatted and mailed. The
  Essentials mail API requires the main thread, so the listener marshals via
  `plugin().executorState().mainThreadExec()`.
- **`EssentialsSafeBlockPredicate`** — moved verbatim from `realty-paper`, except that the
  `IEssentials` instance is resolved in the constructor from the plugin manager rather than in a
  field initialiser, so a failure is reported through `ModuleInitializationException`.
- **`EssentialsAdapterModule`** — `initialize` registers the listener and calls
  `plugin.paperApi().setSafeBlockPredicate(new EssentialsSafeBlockPredicate())`; `shutdown`
  unregisters listeners and resets the predicate to `SafeLocationFinder::defaultIsSafe`.
  `initialize` throws `ModuleInitializationException` if `Essentials` is not enabled.

Build: `compileOnly(project(":realty-paper"))`, `compileOnly` EssentialsX 2.21.2 and paper-api,
shading nothing. Both are provided at runtime by the host.

### 5. The Essentials classpath, and why core keeps the softdepend

`realty-paper` drops the `net.essentialsx:EssentialsX` **compileOnly** dependency and every
`com.earth2me` / `net.ess3` import. It does **not** drop the `paper-plugin.yml` entry:

```yaml
    Essentials:
      load: BEFORE
      join-classpath: true
      required: false
```

Module jars are loaded by a `URLClassLoader` whose parent is Realty's own plugin class loader
(`ModuleLoader.loadModule`). EssX types are therefore visible to the essentials-adapter only if
Realty's class loader can see them, which is exactly what `join-classpath: true` on the softdepend
provides. Removing the entry would compile fine and fail at module load with
`NoClassDefFoundError`.

### 6. `SafeLocationFinder` and the module start-order constraint

`SafeLocationFinder`'s predicate becomes a mutable `volatile Predicate<Block>` with a setter,
because of ordering in `onEnable`: commands are registered at `Realty.java:296` and modules start
at `:305`. By the time the essentials-adapter runs, the `SafeLocationFinder` instance is already
captured inside `TeleportCommand`, so the predicate must be swappable in place rather than injected
at construction.

`RealtyPaperApi` gains:

```java
void setSafeBlockPredicate(@NotNull Predicate<Block> predicate);
```

`RealtyPaperApiImpl` delegates to the single `SafeLocationFinder` instance, which core now
constructs unconditionally with `SafeLocationFinder::defaultIsSafe`.

### 7. Gradle wiring

`settings.gradle.kts` gains:

```kotlin
include("realty-paper-adapters:chat-adapter")
include("realty-paper-adapters:essentials-adapter")
```

Both follow `realty-paper-plan-extension/build.gradle.kts` as the template: `java-library`,
`realty-conventions`, the shadow plugin, and a `processResources` block expanding `${version}` into
`module.yml`. Neither relocates anything, since neither shades anything.

`realty-paper`'s `shadowJar` gains a dependency on `:realty-paper-adapters:chat-adapter:shadowJar`
and copies its output into the main jar under `modules/chat-adapter.jar`.

## Error handling

- **Missing Essentials at module start** — `EssentialsAdapterModule.initialize` throws
  `ModuleInitializationException`. `ModuleLifecycleManager` logs and skips it; core is unaffected.
- **Unresolvable Essentials user** — logged at `WARNING`, that target skipped, other targets in the
  same event still processed. Current behaviour, preserved.
- **A listener throws** — Bukkit catches per-listener, so one broken adapter cannot suppress
  another's delivery, and cannot fail the command or the sweep task that fired the event.
- **chat-adapter extraction fails** — logged at `WARNING`; the plugin enables regardless. A missing
  chat adapter means no chat notifications, which is a degradation, not a fault.

## Testing

- Unit tests per listener with a mocked `Bukkit.getPlayer` / `IEssentials`, asserting: online target
  → chat only; offline target → mail only; multi-target event fans out once per target; null
  `IUser` → warning, no throw, remaining targets processed.
- A test asserting every `MessageKeys.NOTIFICATION_*` constant has exactly one event class using it,
  so a key added later without an event is caught.
- `runServer` smoke pass: both modules appear in `/realty module list`; a buy delivers chat to an
  online previous holder and mail to an offline one; `/realty module reload` on each succeeds.
- Existing command tests updated for the dropped `NotificationService` constructor parameter.

## Migration notes

`NotificationService` is public API in `realty-paper-api`. Removing it is a breaking change for any
consumer outside this repository; the version bump accompanying this work should reflect that.
`realty-paper-plan-extension` and `realty-areashop-importer` do not reference it.
