# Extension Cap Permissions Implementation Plan

**Goal:** Cap how many extensions a player may give a lease, using `realty.leasehold.extensions.<n>` and `realty.leasehold.extensions.unlimited`, with no node meaning 0.
**Spec:** `docs/superpowers/specs/2026-11-21-extension-cap-permissions-design.md`

Backend tests extend `AbstractDatabaseTest` and need a running Docker daemon (Testcontainers MariaDB). A `--tests` run that matches nothing still reports success, so check the test count in the report after each run.

Each task is one layer of the stack `extension-cap/*`, on top of `extension-cap/1-design`.

## Task 1: The cap, its messages and its node

**Files:**
- Create `realty-paper/src/main/java/io/github/md5sha256/realty/command/util/ExtensionCap.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/localisation/MessageKeys.java` (beside `SET_UNLIMITED_NEEDS_NOW` and the `MODIFY_*` keys)
- Modify `realty-paper/src/main/resources/messages.yml` (new `leasehold:` section after `set:`; one key in `modify:`)
- Modify `realty-paper/src/main/resources/paper-plugin.yml` (after `realty.bypass.conflict-of-interest`)
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/ExtensionCapTest.java`
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/ExtensionCapMessagesTest.java`

**Interfaces:**

```java
/** How many extensions a player may give a lease. */
public sealed interface ExtensionCap {

    String UNLIMITED_NODE = "realty.leasehold.extensions.unlimited";
    String NODE_PREFIX = "realty.leasehold.extensions.";

    record Unlimited() implements ExtensionCap {}
    record AtMost(int max) implements ExtensionCap {}

    /**
     * Reads the cap from the sender's permissions. A sender that is not a player is
     * {@link Unlimited}. A player holding {@link #UNLIMITED_NODE} is {@link Unlimited};
     * otherwise the highest {@code NODE_PREFIX + n} they hold, or {@code AtMost(0)} with none.
     */
    static @NotNull ExtensionCap of(@NotNull CommandSender sender);

    /** Whether {@code maxExtensions} (-1 meaning unlimited) is within the cap. */
    boolean allows(int maxExtensions);

    /** The cap as a proposal accept takes it: {@code null} for no cap. */
    @Nullable Integer asLimit();

    /** The message refusing {@code requested}; only for a value {@link #allows} refuses. */
    @NotNull Component refusal(@NotNull MessageContainer messages, int requested);
}
```

```java
// MessageKeys
public static final String LEASEHOLD_EXTENSIONS_ABOVE_CAP = "leasehold.extensions-above-cap";
public static final String LEASEHOLD_EXTENSIONS_UNLIMITED_NOT_ALLOWED = "leasehold.extensions-unlimited-not-allowed";
public static final String MODIFY_EXTENSIONS_ABOVE_CAP = "modify.extensions-above-cap";
```

- [ ] Write the failing tests in `ExtensionCapTest` (`@ExtendWith(MockitoExtension.class)`, a `@Mock Player player` and a `@Mock CommandSender console`). A helper `holds(String... nodes)` stubs `player.getEffectivePermissions()` with `new PermissionAttachmentInfo(player, node, null, true)` for each node; `holdsNegated(String node)` adds one with `false`. `player.hasPermission(ExtensionCap.UNLIMITED_NODE)` is stubbed per test (default `false`, `lenient()`).
  - `noNodeIsZero`: `of(player)` equals `new AtMost(0)`.
  - `unlimitedNodeIsUnlimited`: `hasPermission(UNLIMITED_NODE)` true and `holds("realty.leasehold.extensions.3")`: `Unlimited`.
  - `highestNumberWins`: `holds(".3", ".10", ".5")` (full node names): `AtMost(10)`.
  - `negatedNodeIsIgnored`: `holds(".3")`, `holdsNegated(".10")`: `AtMost(3)`.
  - `malformedSuffixesAreIgnored`: `holds(".abc", ".-2", ".99999999999", ".4", "realty.leasehold.extensionsx.9")`: `AtMost(4)`.
  - `consoleIsUnlimited`: `of(console)` is `Unlimited`.
  - `allows`: `Unlimited` allows -1, 0 and 1000; `AtMost(5)` allows 0 and 5, refuses 6 and -1.
  - `asLimit`: `Unlimited` is `null`; `AtMost(5)` is 5.
- [ ] Write `ExtensionCapMessagesTest`, shaped like `RedefineMessagesTest`: the three new keys are shipped. Add a test that `new AtMost(5).refusal(MESSAGES, 6)` serialises to text containing `5`, and `refusal(MESSAGES, -1)` uses the unlimited key (its text contains "unlimited").
- [ ] Run `./gradlew :realty-paper:test --tests "*ExtensionCap*"` and expect a compile FAIL: `ExtensionCap` does not exist.
- [ ] Implement:
  - `of`: return `Unlimited` when `!(sender instanceof Player)` or `sender.hasPermission(UNLIMITED_NODE)`. `hasPermission` is used here so permission-plugin wildcards (`realty.*`, `realty.leasehold.extensions.*`) count. Otherwise stream `getEffectivePermissions()`, keep `getValue()` true, keep names starting with `NODE_PREFIX`, take the suffix, keep it only if it is all ASCII digits and `Integer.parseInt` does not throw, and take the max; `AtMost(max)`, or `AtMost(0)` when none.
  - `Unlimited.allows` is always true; `AtMost.allows(v)` is `v >= 0 && v <= max`.
  - `AtMost.refusal`: `requested < 0` gives `LEASEHOLD_EXTENSIONS_UNLIMITED_NOT_ALLOWED`, otherwise `LEASEHOLD_EXTENSIONS_ABOVE_CAP`, both with `Placeholder.unparsed("cap", String.valueOf(max))`. `Unlimited.refusal` throws `IllegalStateException`.
  - `messages.yml`:

    ```yaml
    leasehold:
        extensions-above-cap: <prefix> You may grant at most <yellow><cap></yellow> extensions.
        extensions-unlimited-not-allowed: <prefix> You may not grant unlimited extensions.
            You may grant at most <yellow><cap></yellow>.
    ```

    and in `modify:`

    ```yaml
        extensions-above-cap: <prefix> This proposal sets <yellow><proposed></yellow> extensions
            on region <yellow><region></yellow>, but you may grant at most <yellow><cap>.</yellow>
    ```
  - `paper-plugin.yml`:

    ```yaml
      realty.leasehold.extensions.unlimited:
        description: Allows giving a lease unlimited extensions. Without it, realty.leasehold.extensions.<n> caps the count, and no node means 0
        default: op
    ```
- [ ] Run the same command and expect PASS (check the test count).
- [ ] Run `./gradlew build`
- [ ] Commit `feat(leasehold): read a player's extension cap from permissions`

## Task 2: Accepting a proposal under a cap

**Files:**
- Modify `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java:751-770` (`ResolveModificationResult`, `acceptModification`)
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java:1592-1637`
- Modify `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java:1055`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java:2772`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/ModifyCommandGroup.java` (the `switch` in `executeResolve`, which must stay exhaustive)
- Test: modify `realty-backend/src/test/java/io/github/md5sha256/realty/database/RealtyBackendImplTest.java` (nested class `Modifications`)

**Interfaces:**

```java
// RealtyBackend.ResolveModificationResult
/** The proposal sets {@code proposed} extensions; the accepting player may grant at most {@code cap}. */
record AboveExtensionCap(int cap, int proposed) implements ResolveModificationResult {}

// RealtyBackend
/** A manager of the landlord (or an admin) accepts a tenant's pending proposal, promoting it to {@code ACTIVE}. */
default @NotNull ResolveModificationResult acceptModification(@NotNull String worldGuardRegionId,
                                                              @NotNull UUID worldId,
                                                              @NotNull ActorContext ctx) {
    return acceptModification(worldGuardRegionId, worldId, ctx, null);
}

/**
 * As {@link #acceptModification(String, UUID, ActorContext)}, but refuses with
 * {@link ResolveModificationResult.AboveExtensionCap} when the pending proposal sets more
 * extensions than {@code extensionCap}, leaving it pending. A {@code null} cap means no cap.
 */
@NotNull ResolveModificationResult acceptModification(@NotNull String worldGuardRegionId,
                                                      @NotNull UUID worldId,
                                                      @NotNull ActorContext ctx,
                                                      @Nullable Integer extensionCap);

// RealtyPaperApi: the same pair, returning CompletableFuture<RealtyBackend.ResolveModificationResult>,
// the three-argument one a default that passes null.
```

- [ ] Write the failing tests in `Modifications`, each with `createLeasehold(regionId, WORLD_ID, 200.0, 86400, 5, Party.personal(PLAYER_A))`, `rentRegion(..., PLAYER_B)` and a tenant proposal from `ActorContext.player(PLAYER_B, false)`:
  - `acceptAboveCapIsRefusedAndStaysPending`: propose `(null, null, 8)`; accept as `PLAYER_A` with cap 5 returns `AboveExtensionCap(5, 8)`; `listModificationsAwaitingLandlord(Set.of(Party.personal(PLAYER_A)))` still has one entry for the region.
  - `acceptAtCapSucceeds`: propose 5, cap 5: `Success`.
  - `nullCapAcceptsAnyCount`: propose 8, cap `null`: `Success`.
  - `proposalWithoutExtensionsIgnoresCap`: propose `(150.0, null, null)`, cap 0: `Success`.
- [ ] Run `./gradlew :realty-backend:test --tests "*RealtyBackendImplTest*"` and expect a compile FAIL: no four-argument `acceptModification`.
- [ ] Implement:
  - Give `resolveTenantProposal` an `@Nullable Integer extensionCap` parameter (`rejectModification` passes `null`). After the `AWAITING_LANDLORD` check and before `updateStatus`: when `accept && extensionCap != null && mod.newMaxExtensions() != null && mod.newMaxExtensions() > extensionCap`, return `new AboveExtensionCap(extensionCap, mod.newMaxExtensions())` without committing.
  - `RealtyBackendImpl` overrides the four-argument method only; the three-argument one is the interface default.
  - `RealtyPaperApiImpl` implements the four-argument method the way the current one is written (`supplyAsync` on `executorState.dbExec()`), and drops its three-argument override.
  - `ModifyCommandGroup.executeResolve`: add the case `AboveExtensionCap(int cap, int proposed)`, sending `MODIFY_EXTENSIONS_ABOVE_CAP` with `cap`, `proposed` and `region`. The cap is not passed yet (Task 5).
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`
- [ ] Commit `feat(modify): refuse to accept a proposal above an extension cap`

## Task 3: Create and register leasehold

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/CreateCommand.java:116-137` (`executeLeasehold`)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/RegisterCommand.java:104-123` (`executeLeasehold`)

This task is two guard clauses that depend on a live `CommandContext`. The cap logic is covered by Task 1, so verify on a live server.

- [ ] Implement, in both `executeLeasehold` methods, straight after `int maxExtensions = ctx.get(MAX_EXTENSIONS);`:

  ```java
  ExtensionCap cap = ExtensionCap.of(sender);   // `player` in CreateCommand
  if (!cap.allows(maxExtensions)) {
      sender.sendMessage(cap.refusal(messages, maxExtensions));
      return;
  }
  ```
- [ ] Run `./gradlew build`
- [ ] Live server, by hand: a player with `realty.command.create.leasehold` and `realty.leasehold.extensions.5`. `/realty create leasehold capt1 100 1d 6` and `... -1` are refused with the new messages and create nothing; `... 5` succeeds. The same with `/realty register leasehold`. From the console, `register leasehold ... -1` succeeds.
- [ ] Commit `feat(leasehold): cap extensions on create and register`

## Task 4: Set maxextensions

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SetCommandGroup.java:240-270` (`routeTerm`)

- [ ] Implement, in `routeTerm`'s routed callback, after the `PriceSetEvent` check and before the `ApplyNow` branch:

  ```java
  // The landlord's cap decides; a tenant's request is checked when the landlord accepts it.
  if (term.maxExtensions() != null
          && !(routed.outcome() instanceof SetRouting.Outcome.Request)
          && !sender.hasPermission(othersPermission)) {
      ExtensionCap cap = ExtensionCap.of(sender);
      if (!cap.allows(term.maxExtensions())) {
          sender.sendMessage(cap.refusal(messages, term.maxExtensions()));
          return;
      }
  }
  ```

  `set price` and `set duration` build a `Term` with `maxExtensions` `null`, so they skip this. The check covers `ApplyNow` and `Schedule`.
- [ ] Run `./gradlew build`
- [ ] Live server, by hand: a landlord with `.extensions.5` on their vacant lease: `set maxextensions 6` and `-1 --now` are refused, `5` succeeds. While the lease is rented, `set maxextensions 6` (scheduled) is refused and `4` is proposed. The tenant (no nodes) runs `set maxextensions 8` and the request is sent. A player with `realty.command.set.maxextensions.others` sets `-1 --now` on someone else's lease.
- [ ] Commit `feat(set): cap extensions on set maxextensions`

## Task 5: Modify accept passes the cap

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/ModifyCommandGroup.java:156-175` (`executeResolve`)

- [ ] Implement: after `boolean bypass = ...`, `Integer extensionCap = bypass ? null : ExtensionCap.of(sender).asLimit();`, and the `ACCEPT` case calls `api.acceptModification(regionId, worldId, actor, extensionCap)`.
- [ ] Run `./gradlew build`
- [ ] Live server, by hand: the tenant from Task 4 requests 8; the landlord (`.extensions.5`) runs `/realty modify accept` and gets the new refusal; `/realty modify inbox` still lists it. The tenant withdraws and requests 5, and accept succeeds. An op accepts an 8.
- [ ] Commit `feat(modify): cap extensions when a landlord accepts terms`

## Task 6: Subregion dialog

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SubregionState.java:24-25`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SubregionDialog.java` (state creation near line 190, `showCreateDialog` at 443-446, `showConfirmDialog` at 544, `submit` at 594, `validate` at 696, `resolveMaxRenewals` at 756)

The dialog is Paper dialog UI with no test seam. Verify on a live server.

- [ ] Implement:
  - `SubregionState`: add `ExtensionCap extensionCap = new ExtensionCap.AtMost(0);`.
  - Where the state is created (`new SubregionState()`): set `state.extensionCap = ExtensionCap.of(player)`. When it is `AtMost(n)`, set `state.unlimitedRenewals = false` and `state.maxRenewals = String.valueOf(Math.min(3, n))`.
  - `showCreateDialog`: add the `INPUT_UNLIMITED_RENEWALS` toggle only when `state.extensionCap instanceof ExtensionCap.Unlimited`. The count field's label is `"Max renewals (if limited)"` for `Unlimited` and `"Max renewals (up to " + n + ")"` for `AtMost(n)`.
  - `saveCreate`: unchanged. A missing toggle returns `null` from `getBoolean`, which already leaves `unlimitedRenewals` as it is.
  - `validate`: after the `resolveMaxRenewals(state) == null` check, re-read `ExtensionCap cap = ExtensionCap.of(player)` (give `validate` the `Player`; `submit` already has it) and, when `!cap.allows(resolved)`, return `cap.refusal(messages, resolved)`. Re-reading means a permission removed while the dialog was open is applied on submit.
- [ ] Run `./gradlew build`
- [ ] Live server, by hand:
  - A player with `.extensions.5` opens the dialog: no toggle, the field shows 3 and its label says "up to 5"; entering 6 reopens the dialog with the refusal at the top; 5 creates the subregion.
  - A player with no node: the field shows 0 and the subregion is created with 0 extensions.
  - An op: the toggle shows and starts on.
- [ ] Commit `feat(subregion): cap extensions in the subregion dialog`

## Task 7: Help text

**Files:**
- Modify `realty-paper/src/main/resources/messages.yml:351` and `:372-373` (help lines for `create leasehold` and `set maxextensions`)

The project has no `CLAUDE.md`. Release notes are written by the release PR, so the upgrade note (in the spec's Operator impact section) goes into `docs/release-notes/<version>.md` then, first under the upgrade notes, along with the API change (the four-argument `acceptModification` and `ResolveModificationResult.AboveExtensionCap`).

- [ ] `set maxextensions` help: `- Set lease extension limit (-1 for unlimited), up to your extension cap`.
- [ ] Run `./gradlew build`
- [ ] Commit `docs: mention extension caps in help`
