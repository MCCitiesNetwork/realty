# Event-Driven Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `NotificationService` with async Bukkit events fired from every notification call site, and move all delivery (chat, Essentials mail) plus the EssentialsX safe-block predicate into two `plugin-infrastructure` module jars.

**Architecture:** `realty-paper-api` gains `RealtyNotificationEvent` — an abstract async Bukkit `Event` carrying target UUIDs, a pre-rendered Adventure `Component`, the region id/world id, and per-subclass domain fields. Every call site keeps its existing `messages.messageFor(...)` call and passes the resulting `Component` into an event constructor instead of into a service. Two new Gradle subprojects under `realty-paper-adapters/` produce module jars that listen for those events; `realty-paper` itself delivers nothing and no longer references EssentialsX in Java.

**Tech Stack:** Java 21, Gradle Kotlin DSL, PaperMC 1.21.8 API, Adventure, Incendo Cloud, `com.minecraftcitiesnetwork:plugin-infrastructure` module system, EssentialsX 2.21.2 (compileOnly, adapter only), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-21-event-driven-notifications-design.md`

## Global Constraints

- **Java 21.** Toolchain is configured in `build.gradle.kts`; do not change it.
- **No wildcard imports, no static imports.** Every import is an explicit single-class import. In tests use `Assertions.assertEquals(...)`, never a static-imported `assertEquals(...)`.
- **No fully-qualified class names inline.** Use an import statement instead.
- **SQL in MyBatis mappers uses Java text blocks (`"""`).** No task here touches SQL, but the rule stands.
- **Do not change `messages.yml` or any `MessageKeys.NOTIFICATION_*` constant.** Message construction moves verbatim; the rendered text must be byte-identical to today's.
- **`plugin-infrastructure` is a `realty-paper` dependency only.** `realty-backend` and `realty-backend-api` must not gain a dependency on it. The two new adapter subprojects depend on `realty-paper`, so they get it transitively — that is fine.
- **Adapter subprojects shade nothing and relocate nothing.** Everything they need is provided at runtime by the host plugin or the server.
- **`paper-plugin.yml` keeps its `Essentials` softdepend with `join-classpath: true`.** Module jars load through a `URLClassLoader` parented to Realty's plugin class loader; that entry is the only reason EssX types resolve inside the adapter. Removing it compiles fine and fails at runtime with `NoClassDefFoundError`.
- **Commit after every task.** Do not batch tasks into one commit.
- **`./gradlew test` will report 5 failures in `:realty-backend`** (`AgentLogicTest`, `ConcurrencyTest`, `MapperTest`, `RealtyBackendImplTest`, `FreeholdContractMapper.selectTitleHeldRegionTags`) whenever Docker is not running — they are Testcontainers `initializationError`s from `DockerClientProviderStrategy`, not regressions. Verify your work with `./gradlew :realty-paper:test :realty-paper-api:test` and `./gradlew shadowJar` and treat those five as pre-existing if they appear.

---

### Task 1: The event base class

**Files:**
- Create: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RealtyNotificationEvent.java`
- Test: `realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/RealtyNotificationEventTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `RealtyNotificationEvent` with protected constructors `(List<UUID> targetIds, Component message, String regionId, UUID worldId)` and `(UUID targetId, Component message, String regionId, UUID worldId)`; public accessors `List<UUID> targetIds()`, `Component message()`, `String regionId()`, `UUID worldId()`; and the Bukkit pair `getHandlers()` / `getHandlerList()`. Every subclass in Task 2 extends this.

Note on the Bukkit contract: a concrete `Event` must expose a `public HandlerList getHandlers()` **and** a `public static HandlerList getHandlerList()`. Putting both on this abstract base means every subclass shares one `HandlerList`, which is exactly what we want — a listener registering against `RealtyNotificationEvent` then receives every subclass. Subclasses must **not** declare their own `HandlerList`.

`realty-paper-api` has no test source set wired for Bukkit types yet. Check `realty-paper-api/build.gradle.kts` for `testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")` and a JUnit 5 dependency; if either is missing, add it in Step 1 as part of this task.

- [ ] **Step 1: Write the failing test**

Create `realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/RealtyNotificationEventTest.java`:

```java
package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class RealtyNotificationEventTest {

    private static final class TestEvent extends RealtyNotificationEvent {
        TestEvent(UUID targetId, Component message, String regionId, UUID worldId) {
            super(targetId, message, regionId, worldId);
        }

        TestEvent(List<UUID> targetIds, Component message, String regionId, UUID worldId) {
            super(targetIds, message, regionId, worldId);
        }
    }

    @Test
    void singleTargetConstructorWrapsTargetInSingletonList() {
        UUID target = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        TestEvent event = new TestEvent(target, Component.text("hello"), "plot_1", worldId);

        Assertions.assertEquals(List.of(target), event.targetIds());
        Assertions.assertEquals(Component.text("hello"), event.message());
        Assertions.assertEquals("plot_1", event.regionId());
        Assertions.assertEquals(worldId, event.worldId());
    }

    @Test
    void targetIdsAreImmutable() {
        TestEvent event = new TestEvent(UUID.randomUUID(), Component.text("hi"), "plot_1",
                UUID.randomUUID());

        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> event.targetIds().add(UUID.randomUUID()));
    }

    @Test
    void eventIsAsynchronous() {
        TestEvent event = new TestEvent(UUID.randomUUID(), Component.text("hi"), "plot_1",
                UUID.randomUUID());

        Assertions.assertTrue(event.isAsynchronous());
    }

    @Test
    void allSubclassesShareOneHandlerList() {
        TestEvent event = new TestEvent(UUID.randomUUID(), Component.text("hi"), "plot_1",
                UUID.randomUUID());

        Assertions.assertSame(RealtyNotificationEvent.getHandlerList(), event.getHandlers());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :realty-paper-api:test --tests "*RealtyNotificationEventTest*"`
Expected: FAIL — compilation error, `RealtyNotificationEvent` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RealtyNotificationEvent.java`:

```java
package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Base class for every notification Realty emits. Delivery is not Realty's concern: the plugin
 * fires these and adapter modules decide what, if anything, reaches the target.
 *
 * <p>All subclasses share this class's {@link HandlerList}, so a listener registered against
 * {@code RealtyNotificationEvent} receives every subclass.</p>
 *
 * <p>These events are always asynchronous. A listener that touches the Bukkit API must marshal
 * onto the main thread itself.</p>
 */
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
        super(true);
        this.targetIds = List.copyOf(targetIds);
        this.message = message;
        this.regionId = regionId;
        this.worldId = worldId;
    }

    protected RealtyNotificationEvent(@NotNull UUID targetId,
                                      @NotNull Component message,
                                      @NotNull String regionId,
                                      @NotNull UUID worldId) {
        this(List.of(targetId), message, regionId, worldId);
    }

    /**
     * The players this notification is addressed to. Never empty, never mutable.
     */
    public @NotNull List<UUID> targetIds() {
        return this.targetIds;
    }

    /**
     * The message as Realty rendered it, using the server's configured {@code messages.yml}.
     */
    public @NotNull Component message() {
        return this.message;
    }

    public @NotNull String regionId() {
        return this.regionId;
    }

    public @NotNull UUID worldId() {
        return this.worldId;
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

Run: `./gradlew :realty-paper-api:test --tests "*RealtyNotificationEventTest*"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/RealtyNotificationEvent.java realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/RealtyNotificationEventTest.java realty-paper-api/build.gradle.kts
git commit -m "feat(api): add async RealtyNotificationEvent base class"
```

---

### Task 2: The twenty-one event subclasses

**Files:**
- Create: 21 files in `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/`
- Test: `realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/EventSubclassTest.java`

**Interfaces:**
- Consumes: `RealtyNotificationEvent` from Task 1.
- Produces: the 21 event classes listed below. Tasks 3–7 construct these; Tasks 8–9 read them.

Every subclass follows one shape: a `public final class` extending `RealtyNotificationEvent`, a single public constructor taking `(target(s), Component message, String regionId, UUID worldId, <domain fields…>)` in that order, `private final` fields for the domain data, and one accessor per field named exactly as the field. No subclass declares a `HandlerList`.

The complete catalogue — the constructor tail after `(… String regionId, UUID worldId` is given for each:

| Class | Target parameter | Domain tail |
|---|---|---|
| `RegionBoughtEvent` | `UUID targetId` | `UUID buyerId, String buyerName, double price` |
| `OwnershipTransferredEvent` | `UUID targetId` | `UUID newHolderId, String newHolderName` |
| `RegionRentedEvent` | `UUID targetId` | `UUID tenantId, String tenantName, double price, long durationSeconds` |
| `RegionUnrentedEvent` | `UUID targetId` | `UUID tenantId, String tenantName, double refund` |
| `LeaseholdExpiredEvent` | `UUID targetId` | `UUID tenantId, UUID landlordId` |
| `LeaseholdExpiredLandlordEvent` | `UUID targetId` | `UUID tenantId, UUID landlordId` |
| `OutbidEvent` | `UUID targetId` | `UUID newBidderId, double newBidAmount` |
| `AuctionWonEvent` | `UUID targetId` | `UUID winnerId, double winningBid` |
| `AuctionEndedNoBidsEvent` | `UUID targetId` | `UUID auctioneerId` |
| `AuctionCancelledEvent` | `UUID targetId` | `UUID auctioneerId` |
| `BidPaymentExpiredEvent` | `UUID targetId` | `UUID bidderId, double refundAmount` |
| `OfferPlacedEvent` | `UUID targetId` | `UUID offererId, String offererName, double price` |
| `OfferAcceptedEvent` | `UUID targetId` | `UUID offererId, double price` |
| `OfferRejectedEvent` | `List<UUID> targetIds` | `(none)` |
| `OfferWithdrawnEvent` | `UUID targetId` | `UUID offererId, String offererName` |
| `OfferPaymentExpiredEvent` | `UUID targetId` | `UUID offererId, double refundAmount` |
| `AgentInvitedEvent` | `UUID targetId` | `UUID inviterId, String inviterName, UUID inviteeId` |
| `AgentInviteAcceptedEvent` | `UUID targetId` | `UUID inviterId, UUID inviteeId, String inviteeName` |
| `AgentInviteRejectedEvent` | `UUID targetId` | `UUID inviterId, UUID inviteeId, String inviteeName` |
| `AgentInviteWithdrawnEvent` | `UUID targetId` | `UUID inviterId, String inviterName, UUID inviteeId` |
| `AgentRemovedEvent` | `UUID targetId` | `UUID removerId, String removerName, UUID agentId` |

`OfferRejectedEvent` takes a list because `/realty offer rejectall` currently loops one enqueue per offerer (`OfferCommandGroup.java:490`); the single-offerer reject site at `:444` passes `List.of(offererId)`.

- [ ] **Step 1: Write the failing test**

Create `realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/EventSubclassTest.java`:

```java
package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class EventSubclassTest {

    private static final Component MESSAGE = Component.text("rendered");
    private static final String REGION = "plot_1";

    @Test
    void regionBoughtEventCarriesDomainFields() {
        UUID target = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        RegionBoughtEvent event =
                new RegionBoughtEvent(target, MESSAGE, REGION, world, buyer, "Notch", 250.0);

        Assertions.assertEquals(List.of(target), event.targetIds());
        Assertions.assertEquals(buyer, event.buyerId());
        Assertions.assertEquals("Notch", event.buyerName());
        Assertions.assertEquals(250.0, event.price());
    }

    @Test
    void offerRejectedEventAcceptsMultipleTargets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        OfferRejectedEvent event = new OfferRejectedEvent(List.of(first, second), MESSAGE, REGION,
                UUID.randomUUID());

        Assertions.assertEquals(List.of(first, second), event.targetIds());
    }

    @Test
    void everySubclassSharesTheBaseHandlerList() {
        UUID target = UUID.randomUUID();
        UUID world = UUID.randomUUID();

        List<RealtyNotificationEvent> events = List.of(
                new RegionBoughtEvent(target, MESSAGE, REGION, world, target, "Notch", 1.0),
                new OutbidEvent(target, MESSAGE, REGION, world, target, 5.0),
                new AgentRemovedEvent(target, MESSAGE, REGION, world, target, "Notch", target),
                new LeaseholdExpiredEvent(target, MESSAGE, REGION, world, target, target));

        for (RealtyNotificationEvent event : events) {
            Assertions.assertSame(RealtyNotificationEvent.getHandlerList(), event.getHandlers());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :realty-paper-api:test --tests "*EventSubclassTest*"`
Expected: FAIL — compilation error, `RegionBoughtEvent` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create all 21 classes. `RegionBoughtEvent` is the template — every other class is the same shape with the target parameter and domain tail from the catalogue table above:

```java
package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired at the previous title holder when someone buys a region out from under them.
 * Renders {@code notification.region-bought}.
 */
public final class RegionBoughtEvent extends RealtyNotificationEvent {

    private final UUID buyerId;
    private final String buyerName;
    private final double price;

    public RegionBoughtEvent(@NotNull UUID targetId,
                             @NotNull Component message,
                             @NotNull String regionId,
                             @NotNull UUID worldId,
                             @NotNull UUID buyerId,
                             @NotNull String buyerName,
                             double price) {
        super(targetId, message, regionId, worldId);
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.price = price;
    }

    public @NotNull UUID buyerId() {
        return this.buyerId;
    }

    public @NotNull String buyerName() {
        return this.buyerName;
    }

    public double price() {
        return this.price;
    }
}
```

`OfferRejectedEvent` is the one with no domain tail and a list target:

```java
package io.github.md5sha256.realty.api.event;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Fired at every offerer whose offer on a region was rejected — one offerer for
 * {@code /realty offer reject}, all of them for {@code /realty offer rejectall}.
 * Renders {@code notification.offer-rejected}.
 */
public final class OfferRejectedEvent extends RealtyNotificationEvent {

    public OfferRejectedEvent(@NotNull List<UUID> targetIds,
                              @NotNull Component message,
                              @NotNull String regionId,
                              @NotNull UUID worldId) {
        super(targetIds, message, regionId, worldId);
    }
}
```

Give each class a one-or-two-line Javadoc naming who it targets and which `notification.*` key it renders, as above.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :realty-paper-api:test --tests "*EventSubclassTest*"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add realty-paper-api/src/main/java/io/github/md5sha256/realty/api/event/ realty-paper-api/src/test/java/io/github/md5sha256/realty/api/event/EventSubclassTest.java
git commit -m "feat(api): add 21 RealtyNotificationEvent subclasses"
```

---

### Task 3: A test that no notification key can be added without an event

**Files:**
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/localisation/NotificationKeyCoverageTest.java`

**Interfaces:**
- Consumes: `MessageKeys` (`realty-paper`), the event package from Task 2.
- Produces: nothing consumed by later tasks. This is a guard rail.

The mapping is by convention: `notification.region-bought` → `RegionBoughtEvent`. Derive the class name from the key by upper-camel-casing the dash-separated tail and appending `Event`, then assert the class exists. Two keys break the convention and are declared as explicit exceptions in the test: `notification.leasehold-expired-landlord` → `LeaseholdExpiredLandlordEvent` (which the convention actually produces correctly, so it needs no exception) and `notification.ownership-transferred` → `OwnershipTransferredEvent` (likewise). Write the test with an empty exception map and only add entries if a key genuinely cannot follow the convention.

- [ ] **Step 1: Write the failing test**

Create `realty-paper/src/test/java/io/github/md5sha256/realty/localisation/NotificationKeyCoverageTest.java`:

```java
package io.github.md5sha256.realty.localisation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class NotificationKeyCoverageTest {

    private static final String EVENT_PACKAGE = "io.github.md5sha256.realty.api.event.";

    /** Keys whose event class name cannot be derived from the key by convention. */
    private static final Map<String, String> EXCEPTIONS = Map.of();

    @Test
    void everyNotificationKeyHasAnEventClass() throws IllegalAccessException {
        List<String> missing = new ArrayList<>();
        for (Field field : MessageKeys.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !field.getName().startsWith("NOTIFICATION_")) {
                continue;
            }
            String key = (String) field.get(null);
            String className = EXCEPTIONS.getOrDefault(key, deriveClassName(key));
            try {
                Class.forName(EVENT_PACKAGE + className);
            } catch (ClassNotFoundException ex) {
                missing.add(key + " -> " + className);
            }
        }
        Assertions.assertEquals(List.of(), missing,
                "Every notification.* key needs a matching event class");
    }

    private static String deriveClassName(String key) {
        String tail = key.substring(key.indexOf('.') + 1);
        StringBuilder builder = new StringBuilder();
        for (String part : tail.split("-")) {
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }
        return builder.append("Event").toString();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :realty-paper:test --tests "*NotificationKeyCoverageTest*"`
Expected: PASS if Task 2 covered all 20 keys. If it FAILS, the failure message names exactly which key has no event — go back and add that class to Task 2's package before continuing.

- [ ] **Step 3: Reconcile any gap**

If Step 2 listed missing classes, create them following the `RegionBoughtEvent` template from Task 2 with a domain tail appropriate to the call site, then re-run.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :realty-paper:test --tests "*NotificationKeyCoverageTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/test/java/io/github/md5sha256/realty/localisation/NotificationKeyCoverageTest.java
git commit -m "test: assert every notification key has a matching event class"
```

---

### Task 4: Migrate the five agent commands

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AgentInviteCommand.java:73`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AgentInviteAcceptCommand.java:63`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AgentInviteRejectCommand.java:64`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AgentInviteWithdrawCommand.java:74`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AgentRemoveCommand.java:72`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java:628-632`

**Interfaces:**
- Consumes: `AgentInvitedEvent`, `AgentInviteAcceptedEvent`, `AgentInviteRejectedEvent`, `AgentInviteWithdrawnEvent`, `AgentRemovedEvent` from Task 2.
- Produces: five command records whose canonical constructors are now `(RealtyPaperApi api, MessageContainer messages)`. Task 7 relies on that shape when it rewrites `registerCommands`.

Each of these five is a `record` with a `@NotNull NotificationService notificationService` component. Delete that component, delete the `io.github.md5sha256.realty.api.NotificationService` import, add the event import, and replace the `queueNotification` call. Every one of these files already computes `String regionId` and `UUID worldId` locally (see `AgentInviteCommand.java:59-60`); reuse those variables rather than recomputing.

`AgentInviteWithdrawCommand` and `AgentRemoveCommand` already have a `resolveName(UUID)` helper — use it where a name is needed for a UUID that is not the sender.

- [ ] **Step 1: Rewrite `AgentInviteCommand`**

Remove the `notificationService` record component and its import. Replace lines 73-76:

```java
                    Bukkit.getPluginManager().callEvent(new AgentInvitedEvent(
                            inviteeId,
                            messages.messageFor(MessageKeys.NOTIFICATION_AGENT_INVITED,
                                    Placeholder.unparsed("player", player.getName()),
                                    Placeholder.unparsed("region", regionId)),
                            regionId,
                            worldId,
                            player.getUniqueId(),
                            player.getName(),
                            inviteeId));
```

Add imports `org.bukkit.Bukkit` and `io.github.md5sha256.realty.api.event.AgentInvitedEvent`.

- [ ] **Step 2: Rewrite the other four**

`AgentInviteAcceptCommand` line 63 becomes an `AgentInviteAcceptedEvent(inviterId, <same messageFor call>, regionId, worldId, inviterId, player.getUniqueId(), player.getName())`.

`AgentInviteRejectCommand` line 64 becomes an `AgentInviteRejectedEvent(inviterId, <same messageFor call>, regionId, worldId, inviterId, player.getUniqueId(), player.getName())`.

`AgentInviteWithdrawCommand` line 74 becomes an `AgentInviteWithdrawnEvent(inviteeId, <same messageFor call>, regionId, worldId, player.getUniqueId(), resolveName(player.getUniqueId()), inviteeId)`.

`AgentRemoveCommand` line 72 becomes an `AgentRemovedEvent(targetId, <same messageFor call>, regionId, worldId, player.getUniqueId(), player.getName(), targetId)`.

In each: keep the `messages.messageFor(...)` argument list exactly as it is today, drop the record component and the `NotificationService` import, and add `org.bukkit.Bukkit` plus the event import. If a file does not already have a local `worldId` variable, add `UUID worldId = region.world().getUID();` next to the existing `String regionId = region.region().getId();`.

- [ ] **Step 3: Update the construction sites**

In `Realty.java:628-632`, drop the `notificationService` argument from all five constructor calls:

```java
                new AgentInviteCommand(paperApi, messageContainer),
                new AgentInviteAcceptCommand(paperApi, messageContainer),
                new AgentInviteRejectCommand(paperApi, messageContainer),
                new AgentInviteWithdrawCommand(paperApi, messageContainer),
                new AgentRemoveCommand(paperApi, messageContainer),
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :realty-paper:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/command/Agent*.java realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java
git commit -m "refactor: fire agent events instead of calling NotificationService"
```

---

### Task 5: Migrate Buy, Rent and Unrent

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/BuyCommand.java:61-66`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/RentCommand.java:62-66`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/UnrentCommand.java:64-68`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java:637,652,653`

**Interfaces:**
- Consumes: `RegionBoughtEvent`, `RegionRentedEvent`, `RegionUnrentedEvent`.
- Produces: `BuyCommand`, `RentCommand`, `UnrentCommand` with constructors `(RealtyPaperApi api, MessageContainer messages)`.

`BuyCommand` computes `String regionId` at line 52 but no `worldId`; add `UUID worldId = region.world().getUID();` beside it and import `java.util.UUID`. `RentCommand` and `UnrentCommand` need the same check.

`RentResult.Success` exposes `durationSeconds()`; `UnrentResult.Success` exposes `refund()`. Use them for the event's domain tail — they are already in scope inside the `switch` arm.

- [ ] **Step 1: Rewrite `BuyCommand`**

Drop the `notificationService` record component and its import. Replace the body of the `if (success.previousTitleHolderId() != null)` block at lines 61-66:

```java
                    if (success.previousTitleHolderId() != null) {
                        Bukkit.getPluginManager().callEvent(new RegionBoughtEvent(
                                success.previousTitleHolderId(),
                                messages.messageFor(MessageKeys.NOTIFICATION_REGION_BOUGHT,
                                        Placeholder.unparsed("player", sender.getName()),
                                        Placeholder.unparsed("price", CurrencyFormatter.format(success.price())),
                                        Placeholder.unparsed("region", success.regionId())),
                                success.regionId(),
                                worldId,
                                sender.getUniqueId(),
                                sender.getName(),
                                success.price()));
                    }
```

Add imports `org.bukkit.Bukkit`, `io.github.md5sha256.realty.api.event.RegionBoughtEvent`, and `java.util.UUID`.

- [ ] **Step 2: Rewrite `RentCommand` and `UnrentCommand`**

`RentCommand` lines 62-66 become:

```java
                    Bukkit.getPluginManager().callEvent(new RegionRentedEvent(
                            success.landlordId(),
                            messages.messageFor(MessageKeys.NOTIFICATION_REGION_RENTED,
                                    Placeholder.unparsed("player", sender.getName()),
                                    Placeholder.unparsed("price", CurrencyFormatter.format(success.price())),
                                    Placeholder.unparsed("region", success.regionId())),
                            success.regionId(),
                            worldId,
                            sender.getUniqueId(),
                            sender.getName(),
                            success.price(),
                            success.durationSeconds()));
```

`UnrentCommand` lines 64-68 become:

```java
                    Bukkit.getPluginManager().callEvent(new RegionUnrentedEvent(
                            success.landlordId(),
                            messages.messageFor(MessageKeys.NOTIFICATION_REGION_UNRENTED,
                                    Placeholder.unparsed("player", sender.getName()),
                                    Placeholder.unparsed("region", success.regionId()),
                                    Placeholder.unparsed("refund", CurrencyFormatter.format(success.refund()))),
                            success.regionId(),
                            worldId,
                            sender.getUniqueId(),
                            sender.getName(),
                            success.refund()));
```

Both: drop the record component and `NotificationService` import, add `org.bukkit.Bukkit`, the event import, and `java.util.UUID` plus the `worldId` local if absent.

- [ ] **Step 3: Update the construction sites**

`Realty.java` line 637 becomes `new BuyCommand(paperApi, messageContainer),`; lines 652-653 become `new RentCommand(paperApi, messageContainer),` and `new UnrentCommand(paperApi, messageContainer),`.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :realty-paper:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/command/BuyCommand.java realty-paper/src/main/java/io/github/md5sha256/realty/command/RentCommand.java realty-paper/src/main/java/io/github/md5sha256/realty/command/UnrentCommand.java realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java
git commit -m "refactor: fire sale and lease events from Buy/Rent/Unrent"
```

---

### Task 6: Migrate AuctionCommandGroup and OfferCommandGroup

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/AuctionCommandGroup.java:48,215,249,302`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/OfferCommandGroup.java:48,142,290,345,395,444,490`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java:634,649`

**Interfaces:**
- Consumes: `AuctionCancelledEvent`, `OutbidEvent`, `OwnershipTransferredEvent`, `OfferPlacedEvent`, `OfferAcceptedEvent`, `OfferWithdrawnEvent`, `OfferRejectedEvent`.
- Produces: both command groups with the `notificationService` constructor parameter removed.

These two are the largest call sites. Both are classes (not records) taking `NotificationService` as a constructor parameter at line 48 of each — remove that parameter, its field, and the `NotificationService` import.

The nine replacements, each keeping its existing `messages.messageFor(...)` argument list verbatim:

| File:line | Event | Constructor arguments after the message |
|---|---|---|
| `AuctionCommandGroup:215` | `AuctionCancelledEvent` | `regionId, worldId, sender.getUniqueId()` — target is `bidderId` |
| `AuctionCommandGroup:249` | `OutbidEvent` | `regionId, worldId, sender.getUniqueId(), bidAmount` — target is `success.previousBidderId()` |
| `AuctionCommandGroup:302` | `OwnershipTransferredEvent` | `fullyPaid.regionId(), worldId, sender.getUniqueId(), sender.getName()` — target is `fullyPaid.previousTitleHolderId()` |
| `OfferCommandGroup:142` | `OfferPlacedEvent` | `regionId, worldId, sender.getUniqueId(), sender.getName(), price` — target is `success.titleHolderId()` |
| `OfferCommandGroup:290` | `OfferAcceptedEvent` | `regionId, worldId, target.getUniqueId(), price` — target is `target.getUniqueId()`; if no `price` local is in scope in that arm, use the accepted offer's price from the result record, and if the result exposes none, add `0.0` and note it in the commit message |
| `OfferCommandGroup:345` | `OwnershipTransferredEvent` | `fullyPaid.regionId(), worldId, sender.getUniqueId(), sender.getName()` — target is `fullyPaid.previousTitleHolderId()` |
| `OfferCommandGroup:395` | `OfferWithdrawnEvent` | `regionId, worldId, sender.getUniqueId(), sender.getName()` — target is `titleHolderId` |
| `OfferCommandGroup:444` | `OfferRejectedEvent` | `regionId, worldId` — targets are `List.of(target.getUniqueId())` |
| `OfferCommandGroup:490` | `OfferRejectedEvent` | `regionId, worldId` — targets are `success.offererIds()` |

- [ ] **Step 1: Rewrite the auction call sites**

Pattern, using `:249` as the worked example — note that the pre-existing `if (success.previousBidderId() != null)` guard stays:

```java
                                Bukkit.getPluginManager().callEvent(new OutbidEvent(
                                        success.previousBidderId(),
                                        messages.messageFor(MessageKeys.NOTIFICATION_OUTBID,
                                                Placeholder.unparsed("region", regionId),
                                                Placeholder.unparsed("amount", CurrencyFormatter.format(bidAmount))),
                                        regionId,
                                        worldId,
                                        sender.getUniqueId(),
                                        bidAmount));
```

Add `UUID worldId = region.world().getUID();` next to each `String regionId = …` that lacks one.

- [ ] **Step 2: Rewrite the offer call sites**

`:490` collapses the loop into one event — this is the reason the base class takes a list:

```java
                            Bukkit.getPluginManager().callEvent(new OfferRejectedEvent(
                                    success.offererIds(),
                                    messages.messageFor(MessageKeys.NOTIFICATION_OFFER_REJECTED,
                                            Placeholder.unparsed("region", regionId)),
                                    regionId,
                                    region.world().getUID()));
```

Delete the now-unused `Component notification` local above it, and the `for (UUID offererId : success.offererIds())` loop. Check whether the `net.kyori.adventure.text.Component` import is still used elsewhere in the file before removing it.

- [ ] **Step 3: Update the construction sites**

Remove the `notificationService,` argument from `Realty.java:634` (`AuctionCommandGroup`) and `:649` (`OfferCommandGroup`).

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :realty-paper:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/command/AuctionCommandGroup.java realty-paper/src/main/java/io/github/md5sha256/realty/command/OfferCommandGroup.java realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java
git commit -m "refactor: fire auction and offer events instead of calling NotificationService"
```

---

### Task 7: Migrate the sweep task and delete NotificationService

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java:76-79,142,262-270,296,383-441,620-621`
- Delete: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/NotificationService.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/util/TransientNotificationService.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsNotificationService.java`

**Interfaces:**
- Consumes: `AuctionWonEvent`, `AuctionEndedNoBidsEvent`, `BidPaymentExpiredEvent`, `OfferPaymentExpiredEvent`, `LeaseholdExpiredEvent`, `LeaseholdExpiredLandlordEvent`.
- Produces: a `Realty` with no `notificationService` field and a `registerCommands(RealtyPaperApi, ExecutorState, MessageContainer, SafeLocationFinder)` signature (the `NotificationService` parameter is gone). `EssentialsSafeBlockPredicate` still exists in `util/` after this task — Task 10 moves it.

The six sweep-task call sites all sit inside `scheduleTasks()`. `ExpiredBiddingAuction`, `ExpiredBidPayment`, `ExpiredOfferPayment` and `ExpiredLeasehold` are records on `RealtyBackend`; check each for a `worldId()` accessor and use it. `ExpiredLeasehold` has one (it is used at line 434 to look up the world). If `ExpiredBiddingAuction`, `ExpiredBidPayment` or `ExpiredOfferPayment` does **not** expose a world id, add one to the record in `realty-backend-api` and populate it from the query that builds it — the event contract requires it and a nullable world id would be worse.

The leasehold block at lines 425-445 already hops onto the main thread via `scheduler.runTask` because it calls `regionProfileService.applyFlags`. Keep that hop and fire the two leasehold events from inside it; an async-flagged event fired on the main thread is valid, and moving the fire outside would reorder flag application against notification.

- [ ] **Step 1: Rewrite the six sweep call sites**

Line 383 (auction won) becomes:

```java
                    Bukkit.getPluginManager().callEvent(new AuctionWonEvent(
                            auction.winnerId(),
                            this.messageContainer.messageFor(MessageKeys.NOTIFICATION_AUCTION_WON,
                                    Placeholder.unparsed("region", auction.worldGuardRegionId())),
                            auction.worldGuardRegionId(),
                            auction.worldId(),
                            auction.winnerId(),
                            auction.winningBid()));
```

Line 387 (no bids) becomes an `AuctionEndedNoBidsEvent(auction.auctioneerId(), <same messageFor>, auction.worldGuardRegionId(), auction.worldId(), auction.auctioneerId())`.

Line 393 becomes a `BidPaymentExpiredEvent(payment.bidderId(), <same messageFor>, payment.regionId(), payment.worldId(), payment.bidderId(), payment.refundAmount())`.

Line 400 becomes an `OfferPaymentExpiredEvent(payment.offererId(), <same messageFor>, payment.regionId(), payment.worldId(), payment.offererId(), payment.refundAmount())`.

Lines 434 and 438 become `LeaseholdExpiredEvent(expired.tenantId(), <same messageFor>, expired.worldGuardRegionId(), expired.worldId(), expired.tenantId(), expired.landlordId())` and `LeaseholdExpiredLandlordEvent(expired.landlordId(), <same messageFor>, expired.worldGuardRegionId(), expired.worldId(), expired.tenantId(), expired.landlordId())`.

If `AuctionWonEvent` needs a `winningBid` that `ExpiredBiddingAuction` does not expose, add that accessor to the record rather than passing a placeholder.

- [ ] **Step 2: Strip the service wiring**

Delete the `notificationService` field at line 142. Replace lines 261-271 with an unconditional finder and no Essentials branch:

```java
        SafeLocationFinder safeLocationFinder = new SafeLocationFinder();
```

Delete the imports of `NotificationService`, `EssentialsNotificationService` and `TransientNotificationService` (lines 14, 76, 79). Keep the `EssentialsSafeBlockPredicate` import for now — Task 10 removes it. Drop the `this.notificationService,` argument at line 296 and the `@NotNull NotificationService notificationService,` parameter at line 620.

- [ ] **Step 3: Delete the service and its implementations**

```bash
git rm realty-paper-api/src/main/java/io/github/md5sha256/realty/api/NotificationService.java
git rm realty-paper/src/main/java/io/github/md5sha256/realty/util/TransientNotificationService.java
git rm realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsNotificationService.java
```

- [ ] **Step 4: Verify the whole plugin builds and no reference survives**

Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.
Run: `grep -rn "NotificationService" --include=*.java . | grep -v /build/` — expected: no output.
Run: `./gradlew :realty-paper:test :realty-paper-api:test` — expected PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor!: delete NotificationService, fire events from the sweep task

Realty core no longer delivers notifications. Removes a public
realty-paper-api type; consumers outside this repo must migrate to
RealtyNotificationEvent."
```

---

### Task 8: Expose what modules need from Realty

**Files:**
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (accessors near the existing block at :161-181)
- Modify: `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/command/util/SafeLocationFinder.java:62-80`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SafeLocationFinderTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `Realty.executorState()` returning `ExecutorState`, `Realty.paperApi()` returning `RealtyPaperApi`, `RealtyPaperApi.setSafeBlockPredicate(Predicate<Block>)`, and `SafeLocationFinder.setSafetyPredicate(Predicate<Block>)`. Tasks 9 and 10 call all four.

`Realty` currently exposes only `database()`, `logic()`, `settings()`, `regionFlagSettings()`, `realtyTags()` and `taxSettings()`. Modules receive the `Realty` instance in `initialize`, so both new accessors are required.

The ordering constraint that forces a mutable predicate: `registerCommands` runs at `Realty.java:296` and `startModules()` at `:305`. By the time the essentials-adapter initialises, the `SafeLocationFinder` instance is already captured inside `TeleportCommand`, so the predicate has to be swappable on the live instance.

- [ ] **Step 1: Write the failing test**

Create `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SafeLocationFinderTest.java`:

```java
package io.github.md5sha256.realty.command.util;

import org.bukkit.block.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class SafeLocationFinderTest {

    @Test
    void predicateCanBeReplacedAfterConstruction() {
        Predicate<Block> alwaysUnsafe = block -> false;
        SafeLocationFinder finder = new SafeLocationFinder(alwaysUnsafe);

        Assertions.assertSame(alwaysUnsafe, finder.safetyPredicate());

        Predicate<Block> alwaysSafe = block -> true;
        finder.setSafetyPredicate(alwaysSafe);

        Assertions.assertSame(alwaysSafe, finder.safetyPredicate());
    }

    @Test
    void defaultPredicateIsUsedWhenNoneSupplied() {
        SafeLocationFinder finder = new SafeLocationFinder();

        Assertions.assertNotNull(finder.safetyPredicate());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :realty-paper:test --tests "*SafeLocationFinderTest*"`
Expected: FAIL — `setSafetyPredicate` and `safetyPredicate` do not exist.

- [ ] **Step 3: Make the predicate swappable and add the accessors**

In `SafeLocationFinder`, change the predicate field to:

```java
    private volatile Predicate<Block> safetyPredicate;
```

and add:

```java
    /**
     * Replaces the safety predicate on this live instance. Adapter modules call this through
     * {@link io.github.md5sha256.realty.api.RealtyPaperApi#setSafeBlockPredicate(Predicate)} —
     * they start after commands are registered, so the finder is already in use by then.
     */
    public void setSafetyPredicate(@NotNull Predicate<Block> safetyPredicate) {
        this.safetyPredicate = safetyPredicate;
    }

    public @NotNull Predicate<Block> safetyPredicate() {
        return this.safetyPredicate;
    }
```

In `RealtyPaperApi`, add the method and the two imports (`org.bukkit.block.Block`, `java.util.function.Predicate`):

```java
    /**
     * Replaces the predicate used to decide whether a player can safely stand on a block when
     * teleporting to a region. Adapter modules call this from {@code initialize} and reset it
     * from {@code shutdown}.
     */
    void setSafeBlockPredicate(@NotNull Predicate<Block> predicate);
```

In `RealtyPaperApiImpl`, hold the single `SafeLocationFinder` instance and delegate. This means `Realty` must construct the finder before the API impl and pass it in — adjust the `RealtyPaperApiImpl` constructor call at `Realty.java:284-286` accordingly.

In `Realty`, add beside the existing accessors:

```java
    public ExecutorState executorState() {
        return this.executorState;
    }

    public RealtyPaperApi paperApi() {
        return this.paperApi;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :realty-paper:test --tests "*SafeLocationFinderTest*"` — expected PASS.
Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/src/main/java/io/github/md5sha256/realty/ realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SafeLocationFinderTest.java
git commit -m "feat: expose executorState, paperApi and a swappable safe-block predicate"
```

---

### Task 9: The chat-adapter module

**Files:**
- Create: `realty-paper-adapters/chat-adapter/build.gradle.kts`
- Create: `realty-paper-adapters/chat-adapter/src/main/java/io/github/md5sha256/realty/adapter/chat/ChatNotificationListener.java`
- Create: `realty-paper-adapters/chat-adapter/src/main/java/io/github/md5sha256/realty/adapter/chat/ChatAdapterModule.java`
- Create: `realty-paper-adapters/chat-adapter/src/main/resources/module.yml`
- Test: `realty-paper-adapters/chat-adapter/src/test/java/io/github/md5sha256/realty/adapter/chat/ChatNotificationListenerTest.java`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `RealtyNotificationEvent` (Task 1), `Realty.executorState()` (Task 8).
- Produces: a module jar at `realty-paper-adapters/chat-adapter/build/libs/`. Task 11 bundles that jar into the main plugin.

The listener must be unit-testable without a running server, so the player lookup is injected rather than calling `Bukkit.getPlayer` inline. That is the whole reason for the `Function<UUID, Audience>` parameter — the production module passes `Bukkit::getPlayer`, the test passes a map lookup.

- [ ] **Step 1: Write the failing test**

Create `realty-paper-adapters/chat-adapter/src/test/java/io/github/md5sha256/realty/adapter/chat/ChatNotificationListenerTest.java`:

```java
package io.github.md5sha256.realty.adapter.chat;

import io.github.md5sha256.realty.api.event.OfferRejectedEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

class ChatNotificationListenerTest {

    private static final Executor IMMEDIATE = Runnable::run;

    @Test
    void sendsToOnlineTargets() {
        UUID online = UUID.randomUUID();
        List<Component> received = new ArrayList<>();
        Map<UUID, Audience> players = new HashMap<>();
        players.put(online, Audience.audience(new RecordingAudience(received)));

        ChatNotificationListener listener =
                new ChatNotificationListener(IMMEDIATE, players::get);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(online),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(List.of(Component.text("rejected")), received);
    }

    @Test
    void offlineTargetIsSkippedWithoutThrowing() {
        ChatNotificationListener listener =
                new ChatNotificationListener(IMMEDIATE, uuid -> null);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(UUID.randomUUID()),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        Assertions.assertDoesNotThrow(() -> listener.onNotification(event));
    }

    @Test
    void multiTargetEventFansOutOncePerOnlineTarget() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        List<Component> received = new ArrayList<>();
        Map<UUID, Audience> players = new HashMap<>();
        players.put(first, Audience.audience(new RecordingAudience(received)));
        players.put(second, Audience.audience(new RecordingAudience(received)));

        ChatNotificationListener listener =
                new ChatNotificationListener(IMMEDIATE, players::get);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(first, second, offline),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(2, received.size());
    }

    private record RecordingAudience(List<Component> received) implements Audience {
        @Override
        public void sendMessage(net.kyori.adventure.text.ComponentLike message) {
            this.received.add(message.asComponent());
        }
    }
}
```

If `Audience`'s interface shape makes `RecordingAudience` awkward to implement (it has several default methods but `sendMessage(Component)` is the one that matters), implement `sendMessage(@NotNull Component message)` directly instead — adjust the record to whatever single method the installed Adventure version routes plain sends through, and drop the `Audience.audience(...)` wrapper so the map holds the recording audience directly.

- [ ] **Step 2: Wire the subproject and run the test to see it fail**

Add to `settings.gradle.kts`:

```kotlin
include("realty-paper-adapters:chat-adapter")
```

Create `realty-paper-adapters/chat-adapter/build.gradle.kts`, modelled on `realty-paper-plan-extension/build.gradle.kts` but relocating nothing:

```kotlin
plugins {
    `java-library`
    `realty-conventions`
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    compileOnly(project(":realty-paper"))
    compileOnly(project(":realty-paper-api"))
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    testImplementation(project(":realty-paper-api"))
    testImplementation("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}

tasks {
    processResources {
        val projectVersion = version
        filesMatching("module.yml") {
            expand("version" to projectVersion)
        }
    }
}
```

Check `buildSrc/src/main/kotlin/realty-conventions.gradle.kts` for the JUnit 5 dependencies — if the convention plugin does not add them, add `testImplementation` for `org.junit.jupiter:junit-jupiter` and `useJUnitPlatform()` here, matching whatever `realty-paper/build.gradle.kts` does.

Run: `./gradlew :realty-paper-adapters:chat-adapter:test`
Expected: FAIL — `ChatNotificationListener` does not exist.

- [ ] **Step 3: Write the listener and the module**

`ChatNotificationListener.java`:

```java
package io.github.md5sha256.realty.adapter.chat;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Delivers Realty notifications to targets who are online, and drops them otherwise.
 *
 * <p>This is the baseline every server gets. Adapters that can reach offline players — the
 * Essentials mail adapter, for one — listen at a higher priority and handle that case.</p>
 */
public final class ChatNotificationListener implements Listener {

    private final Executor mainThreadExec;
    private final Function<UUID, Audience> playerLookup;

    public ChatNotificationListener(@NotNull Executor mainThreadExec,
                                    @NotNull Function<UUID, Audience> playerLookup) {
        this.mainThreadExec = mainThreadExec;
        this.playerLookup = playerLookup;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        Component message = event.message();
        List<UUID> targets = event.targetIds();
        this.mainThreadExec.execute(() -> {
            for (UUID target : targets) {
                @Nullable Audience audience = this.playerLookup.apply(target);
                if (audience != null) {
                    audience.sendMessage(message);
                }
            }
        });
    }
}
```

`ChatAdapterModule.java`:

```java
package io.github.md5sha256.realty.adapter.chat;

import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sends every Realty notification to its target's chat, when that target is online.
 */
public final class ChatAdapterModule extends SimplePluginModule<Realty> {

    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder) {
        super.initialize(plugin, dataFolder);
        Function<UUID, Audience> lookup = Bukkit::getPlayer;
        registerListener(new ChatNotificationListener(
                plugin.executorState().mainThreadExec(), lookup));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        super.shutdown(plugin);
    }
}
```

`module.yml`:

```yaml
moduleName: chat-adapter
entryClass: io.github.md5sha256.realty.adapter.chat.ChatAdapterModule
author: md5sha256
expectedPluginClass: io.github.md5sha256.realty.Realty
reloadable: true
```

Confirm the manifest filename the loader looks for by reading `ModuleLoader.extractManifest` — the field names come from `ModuleManifest`, and the file name must match exactly what that method reads out of the jar.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :realty-paper-adapters:chat-adapter:test` — expected PASS, 3 tests.
Run: `./gradlew :realty-paper-adapters:chat-adapter:shadowJar` — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts realty-paper-adapters/chat-adapter
git commit -m "feat(chat-adapter): deliver Realty notifications to online players"
```

---

### Task 10: The essentials-adapter module

**Files:**
- Create: `realty-paper-adapters/essentials-adapter/build.gradle.kts`
- Create: `realty-paper-adapters/essentials-adapter/src/main/java/io/github/md5sha256/realty/adapter/essentials/EssentialsMailListener.java`
- Create: `realty-paper-adapters/essentials-adapter/src/main/java/io/github/md5sha256/realty/adapter/essentials/EssentialsSafeBlockPredicate.java`
- Create: `realty-paper-adapters/essentials-adapter/src/main/java/io/github/md5sha256/realty/adapter/essentials/EssentialsAdapterModule.java`
- Create: `realty-paper-adapters/essentials-adapter/src/main/resources/module.yml`
- Test: `realty-paper-adapters/essentials-adapter/src/test/java/io/github/md5sha256/realty/adapter/essentials/EssentialsMailListenerTest.java`
- Delete: `realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsSafeBlockPredicate.java`
- Modify: `realty-paper/build.gradle.kts:23-26` (drop the EssentialsX compileOnly dependency)
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java:77` (drop the import)
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `RealtyNotificationEvent`, `Realty.executorState()`, `Realty.paperApi()`, `RealtyPaperApi.setSafeBlockPredicate(...)`, `SafeLocationFinder.defaultIsSafe`.
- Produces: a module jar. Nothing later consumes it.

Same testability trick as Task 9: the mail send and the online check are injected, so the test needs neither a server nor Essentials on the classpath. The listener takes a `BiConsumer<UUID, String> mailSender` and a `Predicate<UUID> isOnline`.

Mail goes only to targets who are **offline** — the chat adapter already covered the online ones, and mailing both would double up.

Resetting on shutdown uses `SafeLocationFinder.defaultPredicate()`, which already exists as a public static accessor returning `Predicate<Block>`.

- [ ] **Step 1: Write the failing test**

Create `realty-paper-adapters/essentials-adapter/src/test/java/io/github/md5sha256/realty/adapter/essentials/EssentialsMailListenerTest.java`:

```java
package io.github.md5sha256.realty.adapter.essentials;

import io.github.md5sha256.realty.api.event.OfferRejectedEvent;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

class EssentialsMailListenerTest {

    private static final Executor IMMEDIATE = Runnable::run;

    @Test
    void mailsOfflineTargets() {
        UUID offline = UUID.randomUUID();
        List<Map.Entry<UUID, String>> sent = new ArrayList<>();

        EssentialsMailListener listener = new EssentialsMailListener(IMMEDIATE,
                (uuid, text) -> sent.add(Map.entry(uuid, text)),
                uuid -> false);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(offline),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(1, sent.size());
        Assertions.assertEquals(offline, sent.get(0).getKey());
        Assertions.assertEquals("rejected", sent.get(0).getValue());
    }

    @Test
    void onlineTargetIsNotMailed() {
        List<Map.Entry<UUID, String>> sent = new ArrayList<>();

        EssentialsMailListener listener = new EssentialsMailListener(IMMEDIATE,
                (uuid, text) -> sent.add(Map.entry(uuid, text)),
                uuid -> true);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(UUID.randomUUID()),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(List.of(), sent);
    }

    @Test
    void messageIsSerializedToLegacySection() {
        List<Map.Entry<UUID, String>> sent = new ArrayList<>();

        EssentialsMailListener listener = new EssentialsMailListener(IMMEDIATE,
                (uuid, text) -> sent.add(Map.entry(uuid, text)),
                uuid -> false);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(UUID.randomUUID()),
                Component.text("sold", NamedTextColor.RED), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals("§csold", sent.get(0).getValue());
    }

    @Test
    void aFailingSendDoesNotStopRemainingTargets() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<UUID> delivered = new java.util.HashSet<>();

        EssentialsMailListener listener = new EssentialsMailListener(IMMEDIATE,
                (uuid, text) -> {
                    if (uuid.equals(first)) {
                        throw new IllegalStateException("no such user");
                    }
                    delivered.add(uuid);
                },
                uuid -> false);
        RealtyNotificationEvent event = new OfferRejectedEvent(List.of(first, second),
                Component.text("rejected"), "plot_1", UUID.randomUUID());

        listener.onNotification(event);

        Assertions.assertEquals(Set.of(second), delivered);
    }
}
```

Replace `java.util.HashSet` with a proper import at the top rather than the inline qualified name — the global constraint forbids fully-qualified names inline.

- [ ] **Step 2: Wire the subproject and run the test to see it fail**

Add to `settings.gradle.kts`:

```kotlin
include("realty-paper-adapters:essentials-adapter")
```

Create `realty-paper-adapters/essentials-adapter/build.gradle.kts` — same as the chat-adapter's, plus:

```kotlin
    compileOnly("net.essentialsx:EssentialsX:2.21.2") {
        exclude(group = "org.bukkit", module = "bukkit")
        exclude(group = "org.spigotmc", module = "spigot-api")
    }
```

Run: `./gradlew :realty-paper-adapters:essentials-adapter:test`
Expected: FAIL — `EssentialsMailListener` does not exist.

- [ ] **Step 3: Write the listener, the predicate and the module**

`EssentialsMailListener.java`:

```java
package io.github.md5sha256.realty.adapter.essentials;

import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Sends Realty notifications to offline targets as Essentials mail. Online targets are left to
 * the chat adapter, so nobody gets the same notification twice.
 *
 * <p>Mail is a legacy-section format, so the Component is flattened on the way out — RGB and
 * hover/click data do not survive.</p>
 */
public final class EssentialsMailListener implements Listener {

    private final Executor mainThreadExec;
    private final BiConsumer<UUID, String> mailSender;
    private final Predicate<UUID> isOnline;

    public EssentialsMailListener(@NotNull Executor mainThreadExec,
                                  @NotNull BiConsumer<UUID, String> mailSender,
                                  @NotNull Predicate<UUID> isOnline) {
        this.mainThreadExec = mainThreadExec;
        this.mailSender = mailSender;
        this.isOnline = isOnline;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNotification(@NotNull RealtyNotificationEvent event) {
        String legacy = LegacyComponentSerializer.legacySection().serialize(event.message());
        List<UUID> targets = event.targetIds();
        this.mainThreadExec.execute(() -> {
            for (UUID target : targets) {
                if (this.isOnline.test(target)) {
                    continue;
                }
                try {
                    this.mailSender.accept(target, legacy);
                } catch (RuntimeException ex) {
                    // One unresolvable user must not cost the other targets their mail.
                    Bukkit.getLogger().warning(
                            "Realty: failed to mail notification to " + target + ": " + ex.getMessage());
                }
            }
        });
    }
}
```

Add the `org.bukkit.Bukkit` import for that logger call, or pass a `Logger` into the constructor if you prefer to keep the class free of Bukkit statics — either is fine, but the test must still pass unchanged.

`EssentialsSafeBlockPredicate.java` — move the file from `realty-paper/src/main/java/io/github/md5sha256/realty/util/`, change its package to `io.github.md5sha256.realty.adapter.essentials`, and resolve `IEssentials` in a constructor that throws if Essentials is absent:

```java
package io.github.md5sha256.realty.adapter.essentials;

import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.utils.LocationUtil;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Safety predicate that delegates to EssentialsX's {@link LocationUtil#isBlockUnsafe} for
 * deciding whether a player can safely stand at a given feet-level block.
 */
public final class EssentialsSafeBlockPredicate implements Predicate<Block> {

    private final IEssentials essentials;

    public EssentialsSafeBlockPredicate(@NotNull IEssentials essentials) {
        this.essentials = essentials;
    }

    @Override
    public boolean test(@NotNull Block feetBlock) {
        return !LocationUtil.isBlockUnsafe(
                this.essentials,
                feetBlock.getWorld(),
                feetBlock.getX(),
                feetBlock.getY(),
                feetBlock.getZ()
        );
    }
}
```

`EssentialsAdapterModule.java`:

```java
package io.github.md5sha256.realty.adapter.essentials;

import com.earth2me.essentials.Console;
import com.earth2me.essentials.IEssentials;
import com.minecraftcitiesnetwork.pluginInfrastructure.modules.ModuleInitializationException;
import com.minecraftcitiesnetwork.pluginInfrastructure.modules.SimplePluginModule;
import io.github.md5sha256.realty.Realty;
import io.github.md5sha256.realty.command.util.SafeLocationFinder;
import net.ess3.api.IUser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Adds EssentialsX support to Realty: notifications for offline players become Essentials mail,
 * and teleport safety uses EssentialsX's own block checks.
 */
public final class EssentialsAdapterModule extends SimplePluginModule<Realty> {

    @Override
    public void initialize(@NotNull Realty plugin, @NotNull Path dataFolder)
            throws ModuleInitializationException {
        super.initialize(plugin, dataFolder);
        Plugin essentialsPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
        if (!(essentialsPlugin instanceof IEssentials essentials) || !essentialsPlugin.isEnabled()) {
            throw new ModuleInitializationException(
                    "EssentialsX is not installed or not enabled — essentials-adapter cannot start");
        }
        registerListener(new EssentialsMailListener(
                plugin.executorState().mainThreadExec(),
                (uuid, text) -> sendMail(essentials, uuid, text),
                uuid -> Bukkit.getPlayer(uuid) != null));
        plugin.paperApi().setSafeBlockPredicate(new EssentialsSafeBlockPredicate(essentials));
    }

    @Override
    public void shutdown(@NotNull Realty plugin) {
        unregisterListeners();
        plugin.paperApi().setSafeBlockPredicate(SafeLocationFinder.defaultPredicate());
        super.shutdown(plugin);
    }

    private static void sendMail(@NotNull IEssentials essentials,
                                 @NotNull UUID target,
                                 @NotNull String text) {
        IUser user = essentials.getUser(target);
        if (user == null) {
            throw new IllegalStateException("no Essentials user for " + target);
        }
        essentials.getMail().sendMail(user, Console.getInstance(), text);
    }
}
```

`SafeLocationFinder.defaultPredicate()` already exists as a `public static @NotNull Predicate<Block>` at `SafeLocationFinder.java:78-80` — use it as-is, no visibility change needed.

`module.yml`:

```yaml
moduleName: essentials-adapter
entryClass: io.github.md5sha256.realty.adapter.essentials.EssentialsAdapterModule
author: md5sha256
expectedPluginClass: io.github.md5sha256.realty.Realty
reloadable: false
```

`reloadable: false` because the module has no configuration to refresh; a reload would be a no-op that reports success misleadingly.

- [ ] **Step 4: Purge Essentials from core and verify**

```bash
git rm realty-paper/src/main/java/io/github/md5sha256/realty/util/EssentialsSafeBlockPredicate.java
```

Delete the `compileOnly("net.essentialsx:EssentialsX:2.21.2") { … }` block at `realty-paper/build.gradle.kts:23-26`. Delete the `EssentialsSafeBlockPredicate` import at `Realty.java:77`.

**Do not touch the `Essentials` softdepend in `realty-paper/src/main/resources/paper-plugin.yml:25-28.**` It stays exactly as it is — `join-classpath: true` is what makes EssX types visible to the module's class loader.

Run: `./gradlew :realty-paper-adapters:essentials-adapter:test` — expected PASS, 4 tests.
Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.
Run: `grep -rn "com.earth2me\|net.ess3" --include=*.java realty-paper/src` — expected: no output.
Run: `grep -n "essentialsx\|EssentialsX" realty-paper/build.gradle.kts` — expected: only the `runServer` download URL at line ~89, no `compileOnly`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(essentials-adapter): move Essentials mail and safe-block predicate out of core

realty-paper no longer compiles against EssentialsX. The Essentials
softdepend stays in paper-plugin.yml: join-classpath is what puts EssX
types on the class loader the module jar is parented to."
```

---

### Task 11: Bundle chat-adapter into the plugin jar

**Files:**
- Modify: `realty-paper/build.gradle.kts` (shadowJar block)
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (`startModules()`, around :585-595)
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/BundledModuleExtractionTest.java`

**Interfaces:**
- Consumes: the chat-adapter shadowJar from Task 9.
- Produces: nothing consumed later.

The extraction must never overwrite. An operator who deletes `chat-adapter.jar` because they replaced it with something else must not find it back after a restart.

- [ ] **Step 1: Write the failing test**

Create `realty-paper/src/test/java/io/github/md5sha256/realty/BundledModuleExtractionTest.java`:

```java
package io.github.md5sha256.realty;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class BundledModuleExtractionTest {

    @Test
    void extractsWhenAbsent(@TempDir Path moduleDir) throws IOException {
        Path target = moduleDir.resolve("chat-adapter.jar");

        BundledModuleExtractor.extract(target,
                () -> new ByteArrayInputStream("jar-bytes".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("jar-bytes", Files.readString(target));
    }

    @Test
    void neverOverwritesAnExistingFile(@TempDir Path moduleDir) throws IOException {
        Path target = moduleDir.resolve("chat-adapter.jar");
        Files.writeString(target, "operator-replaced-this");

        BundledModuleExtractor.extract(target,
                () -> new ByteArrayInputStream("jar-bytes".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("operator-replaced-this", Files.readString(target));
    }

    @Test
    void missingResourceIsNotFatal(@TempDir Path moduleDir) {
        Path target = moduleDir.resolve("chat-adapter.jar");

        Assertions.assertDoesNotThrow(() -> BundledModuleExtractor.extract(target, () -> null));
        Assertions.assertFalse(Files.exists(target));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :realty-paper:test --tests "*BundledModuleExtractionTest*"`
Expected: FAIL — `BundledModuleExtractor` does not exist.

- [ ] **Step 3: Write the extractor and call it**

Create `realty-paper/src/main/java/io/github/md5sha256/realty/BundledModuleExtractor.java`:

```java
package io.github.md5sha256.realty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Writes a module jar shipped inside the plugin jar out to the modules directory, once.
 *
 * <p>An existing file is never replaced: an operator who removed or swapped a bundled module
 * keeps that choice across restarts.</p>
 */
public final class BundledModuleExtractor {

    private BundledModuleExtractor() {
    }

    public static void extract(@NotNull Path target,
                               @NotNull Supplier<@Nullable InputStream> resource) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream stream = resource.get()) {
            if (stream == null) {
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(stream, target);
        }
    }
}
```

In `Realty.startModules()`, before `this.moduleManager.start()`, extract the bundled jar and log rather than fail on error:

```java
        try {
            Files.createDirectories(moduleDir);
            BundledModuleExtractor.extract(moduleDir.resolve("chat-adapter.jar"),
                    () -> getClass().getClassLoader().getResourceAsStream("modules/chat-adapter.jar"));
        } catch (IOException ex) {
            // No chat adapter means no chat notifications — a degradation, not a reason to fail enable.
            getLogger().warning("Failed to extract the bundled chat-adapter module: " + ex.getMessage());
        }
```

In `realty-paper/build.gradle.kts`, add to the `shadowJar` block:

```kotlin
        dependsOn(":realty-paper-adapters:chat-adapter:shadowJar")
        from(project(":realty-paper-adapters:chat-adapter")
                .tasks.named("shadowJar").map { it.outputs.files.singleFile }) {
            into("modules")
            rename { "chat-adapter.jar" }
        }
```

- [ ] **Step 4: Run test to verify it passes and check the jar**

Run: `./gradlew :realty-paper:test --tests "*BundledModuleExtractionTest*"` — expected PASS, 3 tests.
Run: `./gradlew shadowJar` then `unzip -l realty-paper/build/libs/*-all.jar | grep chat-adapter` — expected: one `modules/chat-adapter.jar` entry.

- [ ] **Step 5: Commit**

```bash
git add realty-paper/build.gradle.kts realty-paper/src/main/java/io/github/md5sha256/realty/ realty-paper/src/test/java/io/github/md5sha256/realty/BundledModuleExtractionTest.java
git commit -m "feat: bundle chat-adapter in the plugin jar and extract it on first enable"
```

---

### Task 12: Documentation and cleanup

**Files:**
- Modify: `CLAUDE.md`
- Delete: `memory-bank/player-notifications-integration-plan.md`
- Modify: `memory-bank/activeContext.md`, `memory-bank/progress.md`, `memory-bank/systemPatterns.md` (only where they describe `NotificationService`)

**Interfaces:**
- Consumes: everything.
- Produces: nothing.

- [ ] **Step 1: Find every stale reference**

Run: `grep -rn "NotificationService\|EssentialsNotification\|TransientNotification" --include=*.md .`
Every hit is either something to rewrite or a doc to delete.

- [ ] **Step 2: Update `CLAUDE.md`**

In the module list, add the two adapter subprojects beside `realty-areashop-importer` and `realty-paper-plan-extension`:

> - **`realty-paper-adapters/chat-adapter`**, **`realty-paper-adapters/essentials-adapter`** — notification delivery modules, loaded by `ModuleLifecycleManager`. `chat-adapter` is bundled in the plugin jar and extracted on first enable.

Replace the `NotificationService` line in the `realty-paper-api` description with `RealtyNotificationEvent`, and add a short subsection under **Domain Patterns**:

> - **Notifications:** Realty core delivers nothing. Every notification is an async
>   `RealtyNotificationEvent` subclass in `realty-paper-api`, carrying a pre-rendered `Component`
>   plus its domain fields. Adapter modules listen and deliver. Because the events are async, a
>   listener that touches the Bukkit API marshals onto the main thread itself.

Under the Essentials note, record why the softdepend survives:

> `realty-paper` does not compile against EssentialsX — the `essentials-adapter` module does. The
> `Essentials` softdepend with `join-classpath: true` stays in `paper-plugin.yml` regardless:
> module jars load through a `URLClassLoader` parented to Realty's class loader, so that entry is
> what makes EssX types resolvable inside the adapter.

- [ ] **Step 3: Retire the abandoned plan**

```bash
git rm memory-bank/player-notifications-integration-plan.md
```

Rewrite any `NotificationService` references found in Step 1 inside the other `memory-bank/` files to describe the event model instead.

- [ ] **Step 4: Full verification**

Run: `./gradlew shadowJar` — expected BUILD SUCCESSFUL.
Run: `./gradlew :realty-paper:test :realty-paper-api:test :realty-paper-adapters:chat-adapter:test :realty-paper-adapters:essentials-adapter:test` — expected all PASS.
Run: `grep -rn "NotificationService" . --include=*.java --include=*.md | grep -v /build/` — expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: describe the event-driven notification model and adapter modules"
```

---

## Manual verification (after Task 12)

The unit tests cover the listeners in isolation; these steps cover the wiring they cannot.

- [ ] Run `./gradlew runServer`. Confirm the log shows `Loaded module chat-adapter` and — with EssentialsX present in `run/plugins/` — `Loaded module essentials-adapter`.
- [ ] `/realty module list` shows both.
- [ ] With two accounts: put region `plot_1` up for sale owned by account A, log A out, buy it as account B. Account A should have Essentials mail waiting on next login and no chat message.
- [ ] Repeat with account A online: A gets a chat message and no mail.
- [ ] `/realty teleport plot_1` still lands somewhere safe, confirming the predicate swap took effect.
- [ ] Delete `plugins/Realty/modules/chat-adapter.jar`, restart, confirm it is re-extracted. Then create an empty file at that path, restart, and confirm it is **not** overwritten.
- [ ] Remove EssentialsX entirely and restart: the plugin enables, the log warns that essentials-adapter could not start, and chat notifications still work.
