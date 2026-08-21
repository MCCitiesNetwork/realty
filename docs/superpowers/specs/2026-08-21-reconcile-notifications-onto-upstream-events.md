# Reconciling event-driven notifications onto upstream's event system

Date: 2026-08-21
Status: approved
Supersedes: `2026-08-21-event-driven-notifications-design.md`

## Why this document exists

The branch `feature/event-driven-notifications` built a 21-class notification event layer, deleted
`NotificationService`, and moved delivery into two adapter module jars. While it was being built,
`origin/main` advanced 15 commits — six of which (`a32ef11`..`c2cc771`) introduced an independent
event system covering the same ground: 47 event classes in the same package, with 13 exact
class-name collisions. A merge of the branch as-is conflicts in 27 files.

This document describes what to keep, what to discard, and what still has to be built on top of
upstream.

## What upstream already does better

- **`RealtyEventDispatch`** places events on the right thread in both directions, and throws rather
  than silently deferring when a *cancellable* event would need a hop — a deferred cancellation
  verdict cannot be reported to the caller. Strictly better than the branch's `NotificationDispatcher`.
- **A pre/post pattern**: cancellable `RegionBuyEvent` before the mutation, non-cancellable
  `RegionBoughtEvent` after. The branch had no cancellable tier at all.
- **Events are synchronous.** Every production event passes `async = false`, so the branch's
  Critical defect — async events fired from the primary thread — cannot occur, and its fix is moot.

## What upstream has not done

- **Notifications are not event-driven.** Only nine events route through `RegionNotificationListener`
  (region buy/rent/unrent plus the lease lifecycle). Agent, offer and auction commands still call
  `notificationService.queueNotification(...)` directly *and* fire a post-event nobody consumes.
- **Two sweeps fire nothing.** `clearExpiredBidPayments()` and `clearExpiredOfferPayments()` notify
  directly with no event at all.
- **`NotificationService` still picks delivery at enable time**, `EssentialsSafeBlockPredicate` is
  still in `realty-paper/util/`, and core still compiles against EssentialsX.
- **`ModuleLifecycleManager` does not exist upstream.** The module system is in the unpushed local
  commit `eb9667f`, so the adapters depend on infrastructure that is not on `main` either.

## Decisions

| Question | Decision |
|---|---|
| Event catalogue | Upstream's 47 classes win, **unmodified**. The branch's 21 are deleted. |
| Where text is rendered | At the fire site. The notification event carries the rendered `Component`. |
| `NotificationService` | **Obsoleted and deleted**, with both implementations and `RegionNotificationListener`. |
| How adapters receive notifications | One **standalone** `RealtyNotificationEvent`, fired alongside the domain event. |
| Threading | Notification events are **synchronous**, fired via `eventDispatch.fireSync(...)`. |
| Dispatcher | Upstream's `RealtyEventDispatch`. The branch's `NotificationDispatcher` is deleted. |

### Why standalone rather than a supertype

An earlier draft reparented ~20 of upstream's post-events onto a shared base so one adapter handler
could catch them all. That was rejected. Verified against the Paper 1.21.8 sources:

- `SimplePluginManager.fireEvent:645-647` consults **only** `event.getHandlers()` — no hierarchy walk.
- `getRegistrationClass:748-761` resolves a listener's handler list by walking up to the class
  *declaring* `getHandlerList()`, found via `getDeclaredMethod`, so it is **not inherited**.
- The generated executor filters with `isAssignableFrom` (`JavaPluginLoader:297`), so a listener on a
  specific event still receives only that event even when the list is shared.

Bukkit's own `EntityDamageByEntityEvent` relies on exactly this: it declares no `HandlerList` and is
therefore delivered to listeners registered on `EntityDamageEvent`.

The consequence is that reparenting requires deleting **both** the `HANDLERS` field and
`getHandlerList()` from every reparented class. Miss one and that event silently keeps its own list
and never reaches the adapters — no error, no warning. That is unacceptable risk on ~20 classes a
colleague merged days ago, and it also pushes presentation (`Component`) onto domain events that
upstream deliberately kept domain-only.

A standalone event has its own `HandlerList`, touches none of their classes, and gives adapters a
single handler with no inheritance subtleties.

## Architecture

### 1. The notification event

New in `realty-paper-api/.../api/event`:

```java
public final class RealtyNotificationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<UUID> targets;
    private final Component message;
    private final WorldGuardRegion region;   // nullable

    public RealtyNotificationEvent(@NotNull List<UUID> targets,
                                   @NotNull Component message,
                                   @Nullable WorldGuardRegion region) { /* ... */ }

    public @NotNull List<UUID> getTargets();      // List.copyOf, rejects empty
    public @NotNull Component getMessage();
    public @Nullable WorldGuardRegion getRegion();

    @Override public @NotNull HandlerList getHandlers();
    public static @NotNull HandlerList getHandlerList();
}
```

Accessors use upstream's `getX()` convention. The class is `final`: nothing should subclass it,
because a subclass that omitted `getHandlerList()` would silently share this list.

**It extends `Event`, not `RealtyRegionEvent`, and its region is nullable.** `RealtyRegionEvent`
requires a live `WorldGuardRegion`, and the two payment-expiry sweeps cannot always produce one — a
refund must still be announced even when the region row or the WorldGuard region has since been
deleted. Tying notification to region resolution would mean losing the notification exactly when
something has already gone wrong. Commands pass the region they already hold; the sweeps pass what
they can.

### 2. Fire sites render and fire

Every notification path fires this event, in addition to whatever domain post-event it already
fires. The existing `messages.messageFor(MessageKeys.NOTIFICATION_*, ...)` call is kept verbatim,
moved from the `queueNotification` argument into the event constructor, so rendered text does not
change.

The direct `queueNotification` calls in `AgentInviteCommand`, `AgentRemoveCommand`,
`AuctionCommandGroup`, `OfferCommandGroup` and `Realty.scheduleTasks()` are deleted. Upstream's
domain post-events keep firing exactly as they do now — untouched.

Where two parties get different messages (lease expiry notifies tenant and landlord with different
text), that is two `RealtyNotificationEvent` fires, not one event with two targets. The multi-target
list is for the case where several people get the *same* message — notably `/realty offer rejectall`.

### 3. The two silent sweeps

`clearExpiredBidPayments()` and `clearExpiredOfferPayments()` gain notification events. Their
records, `RealtyBackend.ExpiredBidPayment` and `ExpiredOfferPayment`, identify a region by string id
alone, so both gain a `@Nullable UUID worldId`, populated from the `RealtyRegionEntity` the methods
already select — **no SQL change and no migration**. Where the region row is missing, `worldId` is
null, the event fires with a null region, and **the refund is still processed unconditionally**.

Adding upstream-style *domain* post-events for these two sweeps is deliberately out of scope: it is
a gap in upstream's catalogue, not in notification delivery, and belongs in its own change.

### 4. Delivery moves into adapter modules

`NotificationService`, `TransientNotificationService`, `EssentialsNotificationService`,
`RegionNotificationListener`, and the Essentials/transient branch in `Realty.onEnable` are deleted.
Core renders and fires; it delivers nothing.

- **`realty-paper-adapters/chat-adapter`** — one `@EventHandler` on `RealtyNotificationEvent`,
  sending `getMessage()` to each online target. Bundled in the plugin jar and extracted on enable if
  absent, never overwriting, so a stock install keeps today's behaviour.
- **`realty-paper-adapters/essentials-adapter`** — one `@EventHandler` at `EventPriority.HIGH`,
  mailing offline targets. Also carries `EssentialsSafeBlockPredicate`, registered through
  `RealtyPaperApi.setSafeBlockPredicate(...)`.

Because notification events are synchronous and fired through `fireSync`, both adapters already run
on the main thread — no marshalling, and none of the branch's async-dispatch machinery survives.

`paper-plugin.yml` keeps its `Essentials` softdepend with `join-classpath: true`: module jars load
through a `URLClassLoader` parented to Realty's plugin class loader, so that entry is the only reason
EssX types resolve inside the adapter at runtime.

### 5. Prerequisite: the module system

`eb9667f` ("Add module support via plugin-infrastructure") is unpushed and absent from `origin/main`.
It lands first, and conflicts in exactly two hand-mergeable files: `Realty.java` and `messages.yml`.

## What carries over, and what dies

**Survives**
- `eb9667f` — the `plugin-infrastructure` module system.
- Both adapter subprojects, retargeted at the standalone event.
- `BundledModuleExtractor` and the chat-adapter bundling, including the never-overwrite guarantee.
- `Realty.executorState()` / `paperApi()`, the `volatile` swappable safe-block predicate, and
  `RealtyPaperApi.setSafeBlockPredicate(...)`.
- The `worldId` addition to the two expiry records — now load-bearing for the sweep notifications.

**Deleted**
- All 21 branch event classes and their base.
- `NotificationDispatcher` and its test — `RealtyEventDispatch` supersedes it.
- Every call-site migration to the branch's events.
- `NotificationKeyCoverageTest` — its key-to-class naming convention does not hold for upstream's
  catalogue.
- `EventBindingTest` — it reflectively checked the branch's 21 constructors; with one notification
  event there is nothing left for it to sweep.
- The async-fired-from-main-thread fix, moot against synchronous events.

## Testing

- Adapter listener tests carry over: online target gets chat only; offline target gets mail only;
  multi-target fan-out; an unresolvable Essentials user does not cost the other targets their mail.
- `RealtyNotificationEvent` contract: immutable target list, empty list rejected, null message and
  null target list rejected, a null region accepted.
- A test asserting no `queueNotification` call and no `NotificationService` reference survives.
- `runServer` smoke pass: both modules load; a buy notifies an online holder by chat and an offline
  one by mail; `/realty teleport` still lands safely.

## Risks

- `realty-paper-api` is published. Deleting `NotificationService` is breaking, on top of a surface
  upstream also just changed.
- Deleting `RegionNotificationListener` removes a class a colleague added days ago. Its nine handlers
  move back to their fire sites as `RealtyNotificationEvent` fires. This should be raised with them
  rather than landed silently.
