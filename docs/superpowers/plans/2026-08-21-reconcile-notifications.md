# Reconciled Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On top of `origin/main`'s existing event system, make every Realty notification a fired `RealtyNotificationEvent` carrying pre-rendered text, delete `NotificationService` entirely, and move delivery into two adapter module jars.

**Architecture:** One new standalone `RealtyNotificationEvent extends Event` in `realty-paper-api`, with its own `HandlerList`, carrying `List<UUID> targets`, a rendered `Component`, and a nullable `WorldGuardRegion`. Every notification fire site renders as it does today and fires this event **alongside** the domain post-event it already fires. Upstream's 47 event classes are not modified. `NotificationService`, both implementations, and the Essentials/transient branch in `onEnable` are deleted; `RegionNotificationListener` is kept and converted to fire notification events instead of delivering; `realty-paper-adapters/chat-adapter` and `.../essentials-adapter` deliver.

**Tech Stack:** Java 21, Gradle Kotlin DSL, PaperMC 1.21.8, Adventure, Incendo Cloud, `com.minecraftcitiesnetwork:plugin-infrastructure`, EssentialsX 2.21.2 (adapter only), JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-08-21-reconcile-notifications-onto-upstream-events.md`

**Prior work:** the abandoned branch is preserved at tag `pre-reconcile-event-driven-notifications`. Several tasks port files from it verbatim — read them there with `git show pre-reconcile-event-driven-notifications:<path>`.

## Global Constraints

- **Java 21.**
- **No wildcard imports, no static imports.** Explicit single-class imports only. In tests, `Assertions.assertEquals(...)`, never static-imported.
- **No fully-qualified class names inline.**
- **Do not modify any existing class in `realty-paper-api/.../api/event/`.** Upstream's 47 events stay exactly as they are. This plan adds one file to that package and touches no other.
- **Do not change `messages.yml` or any `MessageKeys.NOTIFICATION_*` constant.** Rendered text must be byte-identical; message construction moves, verbatim, from a `queueNotification` argument into an event constructor.
- **`paper-plugin.yml` keeps the `Essentials` softdepend with `join-classpath: true`** even after core stops compiling against EssentialsX. Module jars load through a `URLClassLoader` parented to Realty's plugin class loader; that entry is the only reason EssX types resolve inside the adapter. Removing it compiles cleanly and fails at module load.
- **The module manifest file is `module-manifest.yml`** — `ModuleLoader.extractManifest` reads that exact name.
- **Adapter subprojects shade nothing and relocate nothing**, and need their own `compileOnly` on `plugin-infrastructure` (`realty-paper` exposes it as `implementation`, not `api`).
- **Do not add JUnit dependencies or `useJUnitPlatform()`** — `realty-conventions` in `buildSrc/` supplies both to every subproject.
- **Do not commit `CLAUDE.md` or anything under `memory-bank/`** — they are gitignored and deliberately untracked. Edit on disk only.
- Commit after every task.

---

### Task 1: Branch from upstream and land the module system

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (conflict)
- Modify: `realty-paper/src/main/resources/messages.yml` (conflict)

**Interfaces:**
- Produces: a `ModuleLifecycleManager<Realty>` over `plugins/Realty/modules`, `/realty module list|reload`, and `startModules()`/`stopModules()` in the plugin lifecycle. Tasks 6–8 depend on all of it.

`eb9667f` ("Add module support via plugin-infrastructure") exists only on the local, unpushed `main`. It brings the `plugin-infrastructure` dependency, `ModuleCommandGroup`, and the module lifecycle wiring, and it replaces several local utility classes with library equivalents (`DateFormatter`, `DurationParserUtil`, `ComponentSerializer`, `SimpleDateFormatSerializer`). Upstream has moved since, so it conflicts in two files.

- [ ] **Step 1: Create the reconciliation branch**

```bash
git fetch origin
git checkout -b feature/reconcile-notifications origin/main
```

- [ ] **Step 2: Cherry-pick the module system**

```bash
git cherry-pick eb9667f
```

Expect conflicts in `Realty.java` and `messages.yml`. Resolve by hand, keeping **both** sides: upstream's newer `onEnable` content (event dispatch, lease-lifecycle wiring, subregion work) **and** the module manager construction, `startModules()` last in `onEnable`, `stopModules()` first in `onDisable`, and the `ModuleCommandGroup` registration. In `messages.yml`, keep upstream's new lease-lifecycle keys and add the module command keys.

- [ ] **Step 3: Verify**

Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.
Run: `./gradlew test` — expected BUILD SUCCESSFUL (Docker is running, so the Testcontainers suites execute).
Run: `grep -rn "ModuleLifecycleManager" --include=*.java realty-paper/src/main` — expected: hits in `Realty.java` and `ModuleCommandGroup.java`.

- [ ] **Step 4: Commit**

The cherry-pick is already staged; conclude it.

```bash
git add -A
git commit -m "feat: add module support via plugin-infrastructure

Cherry-picked from the unpushed local main; resolved against upstream's
event-dispatch and lease-lifecycle changes."
```

---

### Task 2: The notification event

**Files:**
- Create: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RealtyNotificationEvent.java`
- Test: `realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/RealtyNotificationEventTest.java`
- Modify: `realty-paper-api/build.gradle.kts` (test dependency, if absent)

**Interfaces:**
- Produces: `RealtyNotificationEvent(List<UUID> targets, Component message, @Nullable WorldGuardRegion region)` with `getTargets()`, `getMessage()`, `getRegion()`, `getHandlers()`, `getHandlerList()`. Tasks 4, 5, 6, 7 all use it.

It extends `Event`, **not** `RealtyRegionEvent` — the latter requires a live `WorldGuardRegion`, and the payment-expiry sweeps in Task 5 cannot always produce one. The class is `final`: a subclass omitting `getHandlerList()` would silently share this handler list.

`realty-paper-api` declares paper-api as `compileOnlyApi`, which does not reach the test compile classpath. If `testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")` is not already present, add it.

- [ ] **Step 1: Write the failing test**

```java
package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class RealtyNotificationEventTest {

    private static final Component MESSAGE = Component.text("rendered");

    @Test
    void exposesTargetsAndMessage() {
        UUID target = UUID.randomUUID();
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(List.of(target), MESSAGE, null);

        Assertions.assertEquals(List.of(target), event.getTargets());
        Assertions.assertEquals(MESSAGE, event.getMessage());
        Assertions.assertNull(event.getRegion());
    }

    @Test
    void targetsAreDefensivelyCopiedAndImmutable() {
        List<UUID> mutable = new ArrayList<>();
        mutable.add(UUID.randomUUID());
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(mutable, MESSAGE, null);

        mutable.add(UUID.randomUUID());

        Assertions.assertEquals(1, event.getTargets().size());
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> event.getTargets().add(UUID.randomUUID()));
    }

    @Test
    void rejectsEmptyTargets() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RealtyNotificationEvent(List.of(), MESSAGE, null));
    }

    @Test
    void rejectsNulls() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new RealtyNotificationEvent(null, MESSAGE, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> new RealtyNotificationEvent(List.of(UUID.randomUUID()), null, null));
    }

    @Test
    void isSynchronous() {
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(List.of(UUID.randomUUID()), MESSAGE, null);

        Assertions.assertFalse(event.isAsynchronous());
    }

    @Test
    void handlerListIsShared() {
        RealtyNotificationEvent event =
                new RealtyNotificationEvent(List.of(UUID.randomUUID()), MESSAGE, null);

        Assertions.assertSame(RealtyNotificationEvent.getHandlerList(), event.getHandlers());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :realty-paper-api:test --tests "*RealtyNotificationEventTest*"`
Expected: FAIL — `RealtyNotificationEvent` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package io.github.md5sha256.realty.api.event;

import io.github.md5sha256.realty.api.WorldGuardRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fired whenever Realty has something to tell one or more players. The message is rendered by the
 * fire site from {@code messages.yml}; this event only carries it.
 *
 * <p>Realty itself delivers nothing — adapter modules listen for this event and decide what reaches
 * the target. It is fired alongside, not instead of, the domain event describing what happened.</p>
 *
 * <p>Synchronous: fired through {@code RealtyEventDispatch.fireSync}, so handlers run on the main
 * thread and may use the Bukkit API directly.</p>
 */
public final class RealtyNotificationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final List<UUID> targets;
    private final Component message;
    private final WorldGuardRegion region;

    /**
     * @param targets who should be told; never empty. Several targets means several people get the
     *                <em>same</em> message — different text per person is separate events.
     * @param message the rendered message
     * @param region  the region this concerns, or null when it cannot be resolved — a refund is
     *                still announced when its region has already been deleted
     */
    public RealtyNotificationEvent(@NotNull List<UUID> targets,
                                   @NotNull Component message,
                                   @Nullable WorldGuardRegion region) {
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (this.targets.isEmpty()) {
            throw new IllegalArgumentException("A notification needs at least one target");
        }
        this.message = Objects.requireNonNull(message, "message");
        this.region = region;
    }

    public @NotNull List<UUID> getTargets() {
        return this.targets;
    }

    public @NotNull Component getMessage() {
        return this.message;
    }

    public @Nullable WorldGuardRegion getRegion() {
        return this.region;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :realty-paper-api:test --tests "*RealtyNotificationEventTest*"` — expected PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-paper-api
git commit -m "feat(api): add RealtyNotificationEvent"
```

---

### Task 3: `worldId` on the two expiry records

**Files:**
- Modify: `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java`
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java`
- Modify: `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyBackendImplTest.java` (if it constructs either record)

**Interfaces:**
- Produces: `ExpiredBidPayment(UUID bidderId, double refundAmount, String regionId, @Nullable UUID worldId)` and `ExpiredOfferPayment(UUID offererId, double refundAmount, String regionId, @Nullable UUID worldId)`. Task 5 needs both.

`ExpiredBiddingAuction` and `ExpiredLeasehold` already carry a `worldId` — **leave those two alone.**

No SQL change and no migration are needed: `clearExpiredBidPayments()` and `clearExpiredOfferPayments()` already select a `RealtyRegionEntity`, which is `(int realtyRegionId, String worldGuardRegionId, UUID worldId)`. The world id is already in hand where the result record is built.

Both methods already fall back to `String regionName = region != null ? region.worldGuardRegionId() : "unknown";`. There is no honest fallback for a UUID, so the new component is `@Nullable`, populated `region != null ? region.worldId() : null`.

- [ ] **Step 1: Add the component to both records**

Add `@Nullable UUID worldId` as a fourth component to each, with a Javadoc line saying it is null when the region row has already been deleted.

- [ ] **Step 2: Populate it**

In both methods, compute `UUID worldId = region != null ? region.worldId() : null;` beside the existing `regionName` line, and pass it. **Do not make the refund conditional on it** — the delete and commit already happen independently and must continue to.

- [ ] **Step 3: Verify**

Run: `./gradlew :realty-backend:test` — expected BUILD SUCCESSFUL (Docker is up, so these run for real).
Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add realty-backend realty-backend-api
git commit -m "feat(backend): carry worldId on the two payment-expiry records"
```

---

### Task 4: Fire notifications from the command call sites

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AgentInviteCommand.java`
- Modify: `.../command/AgentInviteAcceptCommand.java`, `AgentInviteRejectCommand.java`, `AgentInviteWithdrawCommand.java`, `AgentRemoveCommand.java`
- Modify: `.../command/AuctionCommandGroup.java`
- Modify: `.../command/OfferCommandGroup.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (constructor arguments)

**Interfaces:**
- Consumes: `RealtyNotificationEvent` (Task 2), upstream's `RealtyEventDispatch`.
- Produces: command classes with no `NotificationService` parameter. Task 5 deletes the interface itself.

Every direct `notificationService.queueNotification(target, component)` call in these files becomes:

```java
eventDispatch.fireSync(new RealtyNotificationEvent(List.of(target), component, region));
```

**Keep the `messages.messageFor(...)` call exactly as it is** — same key, same `Placeholder.unparsed(...)` arguments in the same order. It moves from being the second argument of `queueNotification` to being the second argument of the event constructor.

**Do not touch the domain post-events these files already fire** (`AgentInvitedEvent`, `OfferPlacedEvent`, `AuctionBidPlacedEvent`, and so on). They keep firing exactly as they do now, unchanged. You are adding a notification fire beside them and removing the direct service call.

Each command already has a `WorldGuardRegion region` local — pass it. Where the region is not in scope at the notification point, pass `null` and note it in your report.

These classes take `NotificationService` as a constructor parameter or record component. Remove it, remove the import, and drop the argument at each construction site in `Realty.java`. They will need `RealtyEventDispatch` instead — check how upstream already passes it to `AuctionCommandGroup`/`OfferCommandGroup` and follow that exact pattern for the agent commands.

The reject-all site in `OfferCommandGroup` currently loops one `queueNotification` per offerer with a single shared `Component`. Collapse it into **one** event carrying the whole list — that is what the multi-target list is for. Guard it with `if (!success.offererIds().isEmpty())`, since the event rejects an empty target list.

- [ ] **Step 1: Migrate the five agent commands**
- [ ] **Step 2: Migrate `AuctionCommandGroup`**
- [ ] **Step 3: Migrate `OfferCommandGroup`, including the reject-all collapse**
- [ ] **Step 4: Update the construction sites in `Realty.java`**
- [ ] **Step 5: Verify**

Run: `./gradlew :realty-paper:compileJava` — expected BUILD SUCCESSFUL.
Run: `grep -rn "queueNotification" --include=*.java realty-paper/src/main/java/io/github/md5sha256/realty/command` — expected: no output.
Run: `./gradlew :realty-paper:test` — expected BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: fire notification events from the command call sites"
```

---

### Task 5: Migrate the sweeps and delete NotificationService

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java`
- Delete: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/NotificationService.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/util/TransientNotificationService.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsNotificationService.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/listener/RegionNotificationListener.java` (**keep it** — see below)

**Interfaces:**
- Consumes: `RealtyNotificationEvent`, the Task 3 records.
- Produces: a core with no notification delivery at all. `EssentialsSafeBlockPredicate` still exists in `util/` — Task 7 moves it.

**`RegionNotificationListener` is kept, not deleted.** It already does the right thing: it renders
notifications from domain events, which is precisely the model this plan is completing. Its only
problem is that it *delivers* through `NotificationService`. Change each of its nine handlers to fire
a `RealtyNotificationEvent` instead of calling `notificationService.queueNotification(...)`, keeping
every `MessageKeys` constant, placeholder and target exactly as they are. Its constructor loses the
`NotificationService` parameter and gains `RealtyEventDispatch`. Its `resolveName(UUID)` helper stays
where it is.

Each handler has the event's `WorldGuardRegion` available via `event.getRegion()` — pass it as the
notification's region. Where a handler notifies two parties with *different* text (lease expiry,
lease terminated), that is two `RealtyNotificationEvent` fires, not one event with two targets.

`Realty.scheduleTasks()` also calls `queueNotification` directly for auction end, expired bid payments, and expired offer payments. Migrate those too. For the two payment sweeps, build the `WorldGuardRegion` only if `payment.worldId()` is non-null and the world and WG region both resolve; otherwise pass `null` for the region. **The refund must not become conditional on any of that.**

The leasehold-expiry sweep already hops to the main thread with `scheduler.runTask` because it calls `regionProfileService.applyFlags`. Keep that hop and fire from inside it.

Then delete the four files, the `notificationService` field, the Essentials/transient selection branch in `onEnable` (leaving `SafeLocationFinder safeLocationFinder = new SafeLocationFinder();` — but **keep** the `EssentialsSafeBlockPredicate` line for now; Task 7 removes it), and every `NotificationService` import and parameter.

- [ ] **Step 1: Convert `RegionNotificationListener`'s nine handlers to fire notification events**
- [ ] **Step 2: Migrate the three `scheduleTasks()` call sites**
- [ ] **Step 3: Delete the three files and strip the wiring**

```bash
git rm realty-paper-api/src/main/java/io/github/md5sha256/realty/api/NotificationService.java
git rm realty-paper/src/main/java/io/github/md5sha256/realty/util/TransientNotificationService.java
git rm realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsNotificationService.java
```

- [ ] **Step 4: Verify**

Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.
Run: `grep -rn "NotificationService\|queueNotification" --include=*.java . | grep -v /build/` — expected: no output.
Run: `./gradlew test` — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor!: delete NotificationService; notifications are events only

Removes a published realty-paper-api type. RegionNotificationListener is
kept and now fires notification events instead of delivering directly."
```

---

### Task 6: Expose the module seams

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java`
- Modify: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/util/SafeLocationFinder.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SafeLocationFinderTest.java`

**Interfaces:**
- Produces: `Realty.executorState()`, `Realty.paperApi()`, `RealtyPaperApi.setSafeBlockPredicate(Predicate<Block>)`, `SafeLocationFinder.setSafetyPredicate(...)` / `safetyPredicate()`. Tasks 7 and 8 need them.

Port this from the abandoned branch, where it was reviewed clean:

```bash
git show pre-reconcile-event-driven-notifications:realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SafeLocationFinderTest.java
```

`SafeLocationFinder`'s predicate field becomes `private volatile Predicate<Block>` with a setter and getter. It must be mutable because `registerCommands(...)` runs before `startModules()`, so by the time the Essentials adapter initialises, the finder instance is already captured inside `TeleportCommand`. `volatile` because modules start on the main thread while the finder is consulted from async chunk-load callbacks.

`RealtyPaperApiImpl` gains the finder as a constructor parameter and delegates. **There must be exactly one `SafeLocationFinder` instance** — the one `RealtyPaperApiImpl` delegates to must be the same object `TeleportCommand` received, or `setSafeBlockPredicate` silently mutates a finder nobody consults. Construct it once in `onEnable`, before the API impl, and pass the same local to both.

`SafeLocationFinder.defaultPredicate()` already exists as a `public static Predicate<Block>` — use that name.

- [ ] **Step 1: Write the failing test** (port it from the tag)
- [ ] **Step 2: Run it and confirm it fails** — `setSafetyPredicate`/`safetyPredicate` do not exist
- [ ] **Step 3: Implement the accessors, the volatile field, and the API method**
- [ ] **Step 4: Verify**

Run: `./gradlew :realty-paper:test --tests "*SafeLocationFinderTest*"` — expected PASS.
Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: expose executorState, paperApi and a swappable safe-block predicate"
```

---

### Task 7: The chat-adapter module

**Files:**
- Create: `realty-paper-adapters/chat-adapter/` — `build.gradle.kts`, `ChatAdapterModule.java`, `ChatNotificationListener.java`, `module-manifest.yml`, `ChatNotificationListenerTest.java`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `RealtyNotificationEvent`, `Realty.executorState()`.
- Produces: a module jar. Task 9 bundles it.

Port the whole subproject from the tag — it was reviewed clean:

```bash
git show pre-reconcile-event-driven-notifications:realty-paper-adapters/chat-adapter/build.gradle.kts
```

Two changes from the ported version:

1. The listener now handles the **standalone** `RealtyNotificationEvent`, whose accessors are `getTargets()` and `getMessage()` (upstream's convention), not `targetIds()`/`message()`.
2. **The event is synchronous**, so the listener no longer needs to marshal to the main thread — it is already there. Drop the `Executor` constructor parameter and the `mainThreadExec.execute(...)` wrapper. Keep the injected `Function<UUID, Audience> playerLookup` seam, which is what makes it testable without a server; production passes `Bukkit::getPlayer`.

Behaviour is otherwise the deleted `TransientNotificationService`: send to each online target, drop otherwise.

The three tests that matter: an online target receives the message; an offline target is skipped without throwing; a multi-target event fans out once per online target. `Audience` has no abstract methods, so a recording fake just overrides `sendMessage(Component)`.

Manifest is `module-manifest.yml` at `src/main/resources/`, with `entryClass` exactly the module class's FQN and `expectedPluginClass` exactly `io.github.md5sha256.realty.Realty`.

- [ ] **Step 1: Port the subproject and wire `settings.gradle.kts`**
- [ ] **Step 2: Adapt the listener to the new event and drop the executor**
- [ ] **Step 3: Run the tests and confirm they pass**
- [ ] **Step 4: Verify**

Run: `./gradlew :realty-paper-adapters:chat-adapter:test` — expected PASS.
Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts realty-paper-adapters/chat-adapter
git commit -m "feat(chat-adapter): deliver Realty notifications to online players"
```

---

### Task 8: The essentials-adapter module, and Essentials leaves core

**Files:**
- Create: `realty-paper-adapters/essentials-adapter/` — `build.gradle.kts`, `EssentialsAdapterModule.java`, `EssentialsMailListener.java`, `EssentialsSafeBlockPredicate.java`, `module-manifest.yml`, `EssentialsMailListenerTest.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsSafeBlockPredicate.java`
- Modify: `realty-paper/build.gradle.kts` (drop the EssentialsX `compileOnly`)
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (drop the predicate line and import)
- Modify: `settings.gradle.kts`

Port the subproject from the tag; it was reviewed clean. Same two adaptations as Task 7 — `getTargets()`/`getMessage()`, and no main-thread marshalling since the event is synchronous.

Mail goes **only to offline targets** — chat-adapter has the online ones, and mailing both would double up. One target failing must not cost the others their mail: wrap each send so a throw is logged and the loop continues.

`initialize` throws `IllegalStateException` (not `ModuleInitializationException`) when Essentials is absent or disabled — `SimplePluginModule.initialize` declares no `throws`, so an override cannot re-widen to a checked exception. `ModuleLifecycleManager` catches `ModuleInitializationException | RuntimeException` identically: it logs SEVERE, unloads the module, and leaves core running.

`shutdown` unregisters listeners **and** resets the predicate to `SafeLocationFinder.defaultPredicate()`.

Manifest `reloadable: false` — no configuration to refresh.

Then remove Essentials from core: delete the predicate, delete the `compileOnly("net.essentialsx:EssentialsX:2.21.2")` block (**leave the `runServer` download URL**), and delete the predicate line and import from `onEnable`.

- [ ] **Step 1: Port the subproject and wire `settings.gradle.kts`**
- [ ] **Step 2: Adapt the listener; move the predicate in**
- [ ] **Step 3: Remove Essentials from core**
- [ ] **Step 4: Verify**

Run: `./gradlew :realty-paper-adapters:essentials-adapter:test` — expected PASS.
Run: `grep -rn "com.earth2me\|net.ess3" --include=*.java realty-paper/src` — expected: no output.
Run: `grep -n "essentialsx\|EssentialsX" realty-paper/build.gradle.kts` — expected: only the `runServer` URL.
Run: `git diff --stat` — `paper-plugin.yml` must **not** appear.
Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(essentials-adapter): move Essentials mail and safe-block predicate out of core"
```

---

### Task 9: Bundle chat-adapter, and stage both for runServer

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/BundledModuleExtractor.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/BundledModuleExtractionTest.java`
- Modify: `realty-paper/build.gradle.kts`, `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java`

Port both from the tag; reviewed clean.

**Extraction must never overwrite.** An operator who deleted or replaced `chat-adapter.jar` must not find it restored. `Files.exists(target)` returns first, before the resource is even opened. A failed extraction logs a warning and must not fail plugin enable.

Wire `shadowJar` so the chat-adapter jar lands at `modules/chat-adapter.jar` inside the plugin jar, and stage **both** adapters into `run/plugins/Realty/modules` for `runServer`.

- [ ] **Step 1: Port the extractor and its test; confirm red then green**
- [ ] **Step 2: Wire shadowJar and runServer**
- [ ] **Step 3: Verify**

Run: `./gradlew :realty-paper:test --tests "*BundledModuleExtractionTest*"` — expected PASS.
Run: `./gradlew shadowJar && unzip -l realty-paper/build/libs/*-all.jar | grep chat-adapter` — expected: exactly one `modules/chat-adapter.jar` entry. **Put this output in your report** — it is the only proof the Gradle wiring worked.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: bundle chat-adapter in the plugin jar and extract it on first enable"
```

---

### Task 10: Warn when Essentials is present without its adapter, and document

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java`
- Modify: `CLAUDE.md` (on disk only — gitignored, never `git add`)
- Delete: `memory-bank/player-notifications-integration-plan.md` (on disk)

An Essentials server upgrading to this build silently loses offline mail and the EssX teleport predicate. After `moduleManager.start()`, warn when `isPluginEnabled("Essentials")` is true but `moduleManager.getActiveModules()` has no `essentials-adapter`. No `com.earth2me` import is needed.

Update `CLAUDE.md` on disk to describe: the notification event model, that core delivers nothing, the two adapter subprojects, `module-manifest.yml` as the manifest name, and why the `Essentials` softdepend with `join-classpath: true` must survive.

- [ ] **Step 1: Add the warning**
- [ ] **Step 2: Update the docs on disk**
- [ ] **Step 3: Verify**

Run: `./gradlew test` — expected BUILD SUCCESSFUL.
Run: `git status --short` — expected: `CLAUDE.md` and `memory-bank/` must **not** appear (they are gitignored).

- [ ] **Step 4: Commit**

```bash
git add realty-paper
git commit -m "feat: warn when EssentialsX is installed without the essentials-adapter module"
```

---

## Manual verification (after Task 10)

- [ ] `./gradlew runServer` — both modules appear in `/realty module list`.
- [ ] With two accounts: sell a region owned by account A, log A out, buy it as B. A has Essentials mail on next login and no chat message.
- [ ] Repeat with A online: chat message, no mail.
- [ ] `/realty offer rejectall` on a region with several offers notifies every offerer once.
- [ ] `/realty teleport` still lands somewhere safe.
- [ ] Delete `plugins/Realty/modules/chat-adapter.jar`, restart, confirm it returns. Put an empty file there, restart, confirm it is **not** overwritten.
- [ ] Remove EssentialsX, restart: plugin enables, logs the missing-adapter warning, chat notifications still work.
