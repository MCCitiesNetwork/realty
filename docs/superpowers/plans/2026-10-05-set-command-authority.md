# Set Command Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `/realty set` and `/realty unset` one authority rule and one timing rule on the party model, fold the `modify` term commands into `set`, and make the backend refuse a write when the holder or the tenancy has changed.

**Architecture:** The backend setters take the existing `ActorContext` plus an "only if vacant" input. They check authority, then run an `UPDATE` that also requires the holder to be unchanged and, when asked, the lease to be vacant. A pure function in `realty-paper` decides the outcome of a command (apply now, schedule, request, or a refusal). A router builds the context, reads the contracts, calls the function and hands the outcome to the command on the main thread.

**Tech Stack:** Java 25, Gradle, MyBatis with MariaDB, Testcontainers (needs Docker running), JUnit 5, Mockito, Incendo Cloud commands, Paper.

**Spec:** `docs/superpowers/specs/2026-10-05-set-command-authority-design.md`. Read it before starting.

**Reference branch:** `feat/set-command-authority` holds an earlier build of this design against a `main` without parties. It does not merge. Tasks below name commits on it to copy from with `git show <sha>:<path>`. Copy and adapt; never cherry-pick or merge from it.

## Global Constraints

- Authority is the existing `ActorContext` test. Do not compare player ids directly, and do not read the WorldGuard owner list to decide authority in `SetCommandGroup` or `UnsetCommandGroup`.
- `set authority` is not changed: its node alone decides, and it takes no `--now`.
- Permission nodes keep the shape `realty.command.<group>.<sub>`. The new node is exactly `realty.command.set.now`.
- The flag is exactly `--now`, built as `CommandFlag.<Source>builder("now").build()`. Region arguments on commands that take it use the existing `RegionOrFlagParser.regionOrFlag()`, so the flag works when the region is omitted.
- Every existing signature on `RealtyBackend` and `RealtyPaperApi` keeps compiling for outside callers. New behaviour arrives as overloads; the old signature becomes a `default` method that delegates with `ActorContext.console()` and `vacantOnly = false`.
- No schema migration and no row locks.
- Cancellable events (`PriceSetEvent`, `LeaseModifyProposeEvent`, `TitleTransferEvent`) fire on the main thread.
- Every message whose text contains `<region>` is sent with the `region` placeholder.
- Player-facing text goes in `messages.yml` with a constant in `MessageKeys`.
- This is a public repository. No home paths, usernames or real names in code, comments, commits or docs.
- One commit per task, in the existing style: `type(scope): plain sentence`.
- The pass bar for every task is `./gradlew :realty-backend:test :realty-paper:test compileJava compileTestJava`. The full `./gradlew build` has two unrelated failures in `realty-web` (`StaticSiteTest`) that predate this work.

## Review Focus

1. **The console schedules on a lease whose landlord is an account or group.** There is no player to record as proposer. The command refuses and names `--now`; nothing throws. [Task 1]
2. **`--now` typed by a player without `realty.command.set.now` on a vacant region.** The command still applies, and the vacancy guard stays on. [Task 1]
3. **A region with no contract at all.** The caller sees the command's existing "no contract" message, not "you do not hold this". [Task 1]
4. **A member of a business sets terms on the business's vacant lease.** It succeeds; a player outside the business is refused. [Task 2]
5. **The holder sets a value equal to the current one.** The update matches the row but changes nothing; the result is `Success`. [Task 2]

## File Structure

| File | Responsibility |
|---|---|
| `realty-paper/.../command/util/SetRouting.java` (new) | Pure decision: tenure, holder, outcome |
| `realty-paper/.../command/util/SetRouter.java` (new) | Builds the context, reads contracts, decides, refuses or hands on, on the main thread |
| `realty-backend-api/.../api/RealtyBackend.java` | Guarded overloads and new result records |
| `realty-backend/.../database/RealtyBackendImpl.java` | Guarded setters and the zero-row diagnosis |
| `realty-backend/.../database/mapper/*ContractMapper.java`, `maria/mapper/Maria*ContractMapper.java` | Guard clauses in the `UPDATE` statements |
| `realty-paper-api/.../api/RealtyPaperApi.java`, `realty-paper/.../api/RealtyPaperApiImpl.java` | Pass the guards through; mirror the new results; redraw signs |
| `realty-paper/.../command/SetCommandGroup.java`, `UnsetCommandGroup.java`, `ModifyCommandGroup.java` | Command behaviour |
| `realty-paper/src/main/resources/messages.yml`, `paper-plugin.yml`, `localisation/MessageKeys.java` | Copy and permissions |

`...` stands for `src/main/java/io/github/md5sha256/realty`.

---

### Task 1: The routing decision and its messages

**Files:**
- Create: `realty-paper/src/main/java/io/github/md5sha256/realty/command/util/SetRouting.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SetRoutingTest.java`
- Modify: `realty-paper/src/main/java/io/github/md5sha256/realty/localisation/MessageKeys.java`, `realty-paper/src/main/resources/messages.yml`

**Reference:** commit `ad0effc` (`SetRouting.java`, `SetRoutingTest.java`). Its `Role` enum and `roleOf`/`proposerFor` are replaced by the inputs below.

**Interfaces:**
- Consumes: `ActorContext`, `Party`, `FreeholdContractEntity`, `LeaseholdContractEntity`.
- Produces:

```java
public final class SetRouting {
    public enum Tenure { NONE, FREEHOLD, VACANT_LEASE, RENTED_LEASE, ENDING_LEASE }
    public enum Kind { TERM, HOLDER }
    public enum Refusal { NOT_HOLDER, NEEDS_NOW, NOW_NOT_PERMITTED, LEASE_ENDING,
                          UNLIMITED_NEEDS_NOW, CONSOLE_NEEDS_NOW }

    public sealed interface Outcome {
        record ApplyNow(boolean vacantOnly) implements Outcome {}
        record Schedule() implements Outcome {}
        record Request() implements Outcome {}
        record Refused(@NotNull Refusal reason) implements Outcome {}
    }

    public static @NotNull Tenure tenureOf(@Nullable FreeholdContractEntity freehold,
                                           @Nullable LeaseholdContractEntity lease);
    /** The party that holds the region, or null when there is no contract. */
    public static @Nullable Party holderOf(@Nullable FreeholdContractEntity freehold,
                                           @Nullable LeaseholdContractEntity lease);
    public static boolean isTenant(@NotNull ActorContext actor, @Nullable LeaseholdContractEntity lease);
    /** Whether a scheduled change can record a proposing player. */
    public static boolean canPropose(@NotNull ActorContext actor, @NotNull LeaseholdContractEntity lease);
    public static @NotNull Outcome decide(@NotNull Tenure tenure, @NotNull Kind kind,
                                          boolean tenant, boolean holds, boolean mayUseNow,
                                          boolean nowFlag, boolean unlimitedExtensions, boolean canPropose);
}
```

- Message keys (constant, YAML key, copy):

| Constant | Key | Copy |
|---|---|---|
| `SET_RENTED_NEEDS_NOW` | `set.rented-needs-now` | `<prefix> Region <yellow><region></yellow> is currently rented. Add <green>--now</green> to change this immediately.` |
| `SET_RENTED_NO_NOW_PERMISSION` | `set.rented-no-now-permission` | `<prefix> Region <yellow><region></yellow> is currently rented, and you do not have permission to change this while it is rented.` |
| `SET_UNLIMITED_NEEDS_NOW` | `set.unlimited-needs-now` | `<prefix> Unlimited extensions cannot wait for a renewal on region <yellow><region>.</yellow> Choose a number instead.` |
| `SET_CONSOLE_NEEDS_NOW` | `set.console-needs-now` | `<prefix> Region <yellow><region></yellow> is rented and its landlord is not a player, so the console cannot schedule this. Add <green>--now</green> to change it immediately.` |
| `SET_JUST_RENTED` | `set.just-rented` | `<prefix> Region <yellow><region></yellow> was just rented, so nothing was changed. Run the command again.` |
| `SET_JUST_VACATED` | `set.just-vacated` | `<prefix> Region <yellow><region></yellow> was just vacated, so nothing was changed. Run the command again.` |

**Rules for `decide`, in this order:**

1. `NONE` → `ApplyNow(true)`. The backend then reports that there is no contract.
2. `FREEHOLD` or `VACANT_LEASE`: `holds` → `ApplyNow(!(nowFlag && mayUseNow))`; otherwise `Refused(NOT_HOLDER)`.
3. `RENTED_LEASE` or `ENDING_LEASE` with `nowFlag`: not `holds` → `Refused(NOT_HOLDER)`; not `mayUseNow` → `Refused(NOW_NOT_PERMITTED)`; otherwise `ApplyNow(false)`.
4. `RENTED_LEASE` or `ENDING_LEASE` without `nowFlag`, `Kind.HOLDER`: not `holds` → `Refused(NOT_HOLDER)`; otherwise `Refused(mayUseNow ? NEEDS_NOW : NOW_NOT_PERMITTED)`.
5. `RENTED_LEASE` or `ENDING_LEASE` without `nowFlag`, `Kind.TERM`: neither `tenant` nor `holds` → `Refused(NOT_HOLDER)`. Otherwise `ENDING_LEASE` → `Refused(LEASE_ENDING)`; `unlimitedExtensions` → `Refused(UNLIMITED_NEEDS_NOW)`; `tenant` → `Request()`; not `canPropose` → `Refused(CONSOLE_NEEDS_NOW)`; else `Schedule()`.

**Rules for the helpers:**

- `tenureOf`: a lease wins if both are present. No tenant → `VACANT_LEASE`. Tenant and a non-null `terminationEffectiveDate` → `ENDING_LEASE`. Tenant otherwise → `RENTED_LEASE`. Freehold only → `FREEHOLD`. Neither → `NONE`.
- `holderOf`: lease → `lease.landlord()`. Freehold → `Party.personal(titleHolderId)` when there is a title holder, otherwise `freehold.authority()`. Neither → `null`.
- `isTenant`: the actor has a player and it equals `lease.tenantId()`.
- `canPropose`: the actor has a player, or the landlord is a `Party.Personal`.

- [ ] **Step 1: Write the failing test**

One `@ParameterizedTest` over `decide`, as `tenure, kind, tenant, holds, mayNow, flag, unlimited, canPropose → expected`:

```
NONE,          TERM,   false, false, false, false, false, true  → ApplyNow(true)
FREEHOLD,      TERM,   false, true,  false, false, false, true  → ApplyNow(true)
FREEHOLD,      TERM,   false, false, false, false, false, true  → Refused(NOT_HOLDER)
FREEHOLD,      HOLDER, false, true,  false, false, false, true  → ApplyNow(true)
VACANT_LEASE,  TERM,   false, true,  false, false, true,  true  → ApplyNow(true)
VACANT_LEASE,  TERM,   false, true,  false, true,  false, true  → ApplyNow(true)
VACANT_LEASE,  TERM,   false, true,  true,  true,  false, true  → ApplyNow(false)
VACANT_LEASE,  HOLDER, false, false, false, false, false, true  → Refused(NOT_HOLDER)
RENTED_LEASE,  TERM,   false, true,  false, false, false, true  → Schedule
RENTED_LEASE,  TERM,   false, true,  false, false, false, false → Refused(CONSOLE_NEEDS_NOW)
RENTED_LEASE,  TERM,   true,  false, false, false, false, true  → Request
RENTED_LEASE,  TERM,   true,  true,  true,  false, false, true  → Request
RENTED_LEASE,  TERM,   true,  true,  true,  true,  false, true  → ApplyNow(false)
RENTED_LEASE,  TERM,   true,  false, true,  true,  false, true  → Refused(NOT_HOLDER)
RENTED_LEASE,  TERM,   false, false, false, false, false, true  → Refused(NOT_HOLDER)
RENTED_LEASE,  TERM,   false, true,  false, false, true,  true  → Refused(UNLIMITED_NEEDS_NOW)
RENTED_LEASE,  TERM,   false, true,  true,  true,  true,  true  → ApplyNow(false)
RENTED_LEASE,  TERM,   false, true,  false, true,  false, true  → Refused(NOW_NOT_PERMITTED)
RENTED_LEASE,  TERM,   false, true,  true,  true,  false, false → ApplyNow(false)
RENTED_LEASE,  HOLDER, false, true,  true,  false, false, true  → Refused(NEEDS_NOW)
RENTED_LEASE,  HOLDER, false, true,  false, false, false, true  → Refused(NOW_NOT_PERMITTED)
RENTED_LEASE,  HOLDER, false, true,  true,  true,  false, true  → ApplyNow(false)
RENTED_LEASE,  HOLDER, true,  false, true,  false, false, true  → Refused(NOT_HOLDER)
ENDING_LEASE,  TERM,   false, true,  false, false, false, true  → Refused(LEASE_ENDING)
ENDING_LEASE,  TERM,   true,  false, false, false, false, true  → Refused(LEASE_ENDING)
ENDING_LEASE,  TERM,   false, true,  true,  true,  false, true  → ApplyNow(false)
ENDING_LEASE,  HOLDER, false, true,  true,  false, false, true  → Refused(NEEDS_NOW)
```

Helper tests, by name: `tenureOfLeaseWithoutTenantIsVacant`, `tenureOfLeaseWithTenantIsRented`, `tenureOfLeaseWithTerminationDateIsEnding`, `tenureOfFreehold`, `tenureOfNothingIsNone`, `holderOfLeaseIsItsLandlord`, `holderOfSoldFreeholdIsItsTitleHolder`, `holderOfUnsoldFreeholdIsItsAuthority`, `holderOfNothingIsNull`, `tenantIsTheTenant`, `consoleIsNeverTheTenant`, `playerCanAlwaysPropose`, `consoleCanProposeForAPlayerLandlord`, `consoleCannotProposeForAnAccountLandlord`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :realty-paper:test --tests '*SetRoutingTest'`
Expected: compilation failure, `SetRouting` does not exist.

- [ ] **Step 3: Implement `SetRouting`, and add the six message keys and their copy to the `set:` block**

- [ ] **Step 4: Run it and confirm it passes**

Run: `./gradlew :realty-paper:test --tests '*SetRoutingTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(set): decide in one place whether a change applies now, later or not at all"
```

---

### Task 2: Guarded term setters in the backend

**Files:**
- Modify: `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java` (the `setPrice`, `unsetPrice`, `setDuration`, `setMaxRenewals` blocks)
- Modify: `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java` (the same four methods)
- Modify: `LeaseholdContractMapper.java`, `FreeholdContractMapper.java`, `MariaLeaseholdContractMapper.java`, `MariaFreeholdContractMapper.java`, and any test that calls the changed mapper methods
- Modify: `realty-paper/.../command/SetCommandGroup.java`, `UnsetCommandGroup.java` (new `case` branches only, so the build stays green)
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/GuardedTermSetterTest.java`

**Reference:** commits `833b998` and `4c07e0b` (guard clauses, `rollback(true)` placement, the `WriteRefusal` diagnosis helpers, the two-session tests). There the guard compared a player id; here it follows the behaviour below.

**Interfaces:**
- Produces, on `RealtyBackend`:

```java
@NotNull SetPriceResult setPrice(@NotNull String worldGuardRegionId, @NotNull UUID worldId, double price,
                                 @NotNull ActorContext ctx, boolean vacantOnly);
@NotNull UnsetPriceResult unsetPrice(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                     @NotNull ActorContext ctx);
@NotNull SetDurationResult setDuration(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                       long durationSeconds, @NotNull ActorContext ctx, boolean vacantOnly);
@NotNull SetMaxRenewalsResult setMaxRenewals(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                             int maxRenewals, @NotNull ActorContext ctx, boolean vacantOnly);
```

- New records: `NotAuthorized()` on all four results. `Occupied()` on `SetPriceResult`, `SetDurationResult`, `SetMaxRenewalsResult`.
- The four existing signatures become `default` methods delegating with `ActorContext.console()` and `false`.
- In `RealtyBackendImpl`, package-private: `enum WriteRefusal { HOLDER_DIFFERS, OCCUPIED, OTHER }`, `diagnoseLeaseholdRefusal(...)`, `diagnoseFreeholdRefusal(...)`. Task 3 reuses them.

**Behaviour:**

1. Read the contract. Existing pre-checks stay and run first (`NoContract`, `AuctionExists`, the payment-in-progress results, `BelowCurrentExtensions`).
2. Authority: the holder is the lease's landlord, or the freehold's title holder, or its authority when it has no title holder. If `!ctx.mayManage(holder)` → `NotAuthorized`, with no write.
3. End the read with `wrapper.session().rollback(true)`. MariaDB 11.7 otherwise raises "Record has changed since last read" on a stale snapshot.
4. Guarded `UPDATE`. Unless `ctx.bypass()`, it requires the holder to be the one just checked: for a lease `lc.landlordPartyId = <the landlord's party id>`; for a freehold a null-safe match on `fc.titleHolderId` (`<=>`). When `vacantOnly`, a lease also requires `lc.tenantId IS NULL`. A freehold ignores `vacantOnly`. Other callers of the changed mapper methods pass "no holder condition" and `false`.
5. No row changed: roll back, re-read committed state, and answer in this order: holder differs from the one checked → `NotAuthorized`; `vacantOnly` and a tenant is present → `Occupied`; otherwise `UpdateFailed`. A refusal writes no history row.
6. In `SetCommandGroup` and `UnsetCommandGroup`, map the new results for now: `NotAuthorized` → `SET_NO_PERMISSION` (`UNSET_NO_PERMISSION` in unset), `Occupied` → `SET_JUST_RENTED` with the `region` placeholder. Tasks 5 and 6 rework these handlers.

- [ ] **Step 1: Write the failing test**

`GuardedTermSetterTest extends AbstractDatabaseTest`, with the fixtures `RealtyBackendImplTest` uses. A player's context is `ActorContext.player(id, false)`. A lease is `logic.createLeasehold(regionId, WORLD_ID, 200.0, 3600, 5, Party.personal(PLAYER_A))`; renting is `logic.rentRegion(regionId, WORLD_ID, PLAYER_B)`; a freehold is `logic.createFreehold(regionId, WORLD_ID, 500.0, Party.personal(AUTHORITY), PLAYER_A)`. For an account landlord use `Party.account(<id>, <kind>)` as other backend tests on this branch do, and a context `new ActorContext(PLAYER_C, Set.of(thatAccount), Set.of(), false)`.

Each test asserts the result type and the stored state.

```java
@Test void landlordSetsPriceOnVacantLease()            // Success; price 300.0
@Test void strangerIsRefusedOnLease()                  // NotAuthorized for price, duration, extensions; nothing changes
@Test void memberOfAnAccountLandlordSetsTerms()        // context managing the account: Success
@Test void outsiderOfAnAccountLandlordIsRefused()      // ActorContext.player(PLAYER_C, false): NotAuthorized
@Test void rentedLeaseIsOccupiedWhenVacantOnly()       // Occupied for all three; nothing changes
@Test void rentedLeaseChangesWhenNotVacantOnly()       // Success; price 300.0
@Test void bypassActsForAnyone()                       // ActorContext.player(PLAYER_C, true): Success
@Test void formerTitleHolderCannotRepriceFreehold()    // after setTitleHolder to PLAYER_B: PLAYER_A gets NotAuthorized for setPrice and unsetPrice
@Test void titleHolderRepricesFreehold()               // Success; price 750.0
@Test void managerOfTheAuthorityRepricesAnUnsoldFreehold() // freehold with null title holder, context of AUTHORITY: Success
@Test void authorityCannotRepriceASoldFreehold()       // title holder PLAYER_A, context of AUTHORITY: NotAuthorized
@Test void settingTheSameValueSucceeds()               // setPrice 200.0 twice and setDuration 3600: Success each time
@Test void oldSignaturesStillApplyUnconditionally()    // rented: setPrice(id, WORLD_ID, 300.0) is Success
@Test void rentCommittedAfterTheFirstReadIsAnsweredOccupied()
@Test void landlordChangedAfterTheFirstReadIsAnsweredHolderDiffers()
@Test void titleHolderChangedAfterTheFirstReadIsAnsweredHolderDiffers()
```

The last three interleave two real sessions at mapper level, as the reference commit `4c07e0b` does: read in session one, commit the change from session two, run the guarded `UPDATE` (assert 0 rows), assert the diagnosis helper's answer.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :realty-backend:test --tests '*GuardedTermSetterTest'`
Expected: compilation failure.

- [ ] **Step 3: Add the overloads, records and `default` delegations to `RealtyBackend`**

- [ ] **Step 4: Add the guard parameters to the mappers and the MariaDB `UPDATE` statements; fix other callers**

- [ ] **Step 5: Implement the four guarded methods and the two diagnosis helpers in `RealtyBackendImpl`**

- [ ] **Step 6: Add the new `case` branches in `SetCommandGroup` and `UnsetCommandGroup`**

- [ ] **Step 7: Run the test, then the pass bar**

Expected: PASS and green.

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(backend): a term change is refused when the holder or the tenancy has changed"
```

---

### Task 3: Guarded holder setters, through to the plugin API

**Files:**
- Modify: `RealtyBackend.java` (`setTenant`, `setTitleHolder`, `setLandlord`), `RealtyBackendImpl.java`, the four mapper files
- Modify: `realty-paper-api/.../api/RealtyPaperApi.java`, `realty-paper/.../api/RealtyPaperApiImpl.java`
- Modify: `SetCommandGroup.java`, `UnsetCommandGroup.java`, `TransferCommand.java` (new `case` branches only)
- Modify: `realty-paper/src/test/java/io/github/md5sha256/realty/api/RealtyPaperApiImplTest.java`
- Test: `realty-backend/src/test/java/io/github/md5sha256/realty/database/GuardedHolderSetterTest.java`

**Reference:** commit `5eeecc0`.

**Interfaces:**
- Consumes: Task 2's overloads and diagnosis helpers.
- Produces, on `RealtyBackend`:

```java
@NotNull SetTenantResult setTenant(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                   @Nullable UUID tenantId, @NotNull ActorContext ctx, boolean vacantOnly);
@NotNull SetTitleHolderResult setTitleHolder(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                             @Nullable UUID titleHolderId, @NotNull ActorContext ctx);
@NotNull SetLandlordResult setLandlord(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                                       @NotNull Party newLandlord, @NotNull ActorContext ctx, boolean vacantOnly);
```

- New backend records: `NotAuthorized()` on `SetTenantResult` and `SetTitleHolderResult`; `Occupied()` on `SetTenantResult` and `SetLandlordResult`. `setLandlord` keeps `NotAllowedToReassign` and `NotAllowedToAssign` for authority.
- The existing `setTenant` and `setTitleHolder` become `default` methods delegating with `ActorContext.console()` (and `false`). The existing `setLandlord(…, ctx)` becomes a `default` delegating with `false`.
- Produces, on `RealtyPaperApi`:

```java
CompletableFuture<RealtyBackend.SetPriceResult> setPrice(String regionId, UUID worldId, double price,
                                                         ActorContext ctx, boolean vacantOnly);
CompletableFuture<RealtyBackend.UnsetPriceResult> unsetPrice(String regionId, UUID worldId, ActorContext ctx);
CompletableFuture<RealtyBackend.SetDurationResult> setDuration(String regionId, UUID worldId, long durationSeconds,
                                                               ActorContext ctx, boolean vacantOnly);
CompletableFuture<RealtyBackend.SetMaxRenewalsResult> setMaxRenewals(String regionId, UUID worldId, int maxRenewals,
                                                                     ActorContext ctx, boolean vacantOnly);
CompletableFuture<SetTitleHolderResult> setTitleHolder(WorldGuardRegion region, @Nullable Party titleHolder,
                                                       ActorContext ctx);
CompletableFuture<SetTenantResult> setTenant(WorldGuardRegion region, @Nullable Party tenant,
                                             ActorContext ctx, boolean vacantOnly);
CompletableFuture<SetLandlordResult> setLandlord(WorldGuardRegion region, Party landlord,
                                                 ActorContext ctx, boolean vacantOnly);
```

- New plugin-level records: `NotAuthorized(@NotNull String regionId)` on `RealtyPaperApi.SetTitleHolderResult` and `SetTenantResult`; `Occupied(@NotNull String regionId)` on `SetTenantResult` and `SetLandlordResult`.
- Each existing `RealtyPaperApi` signature for these seven becomes a `default` delegating with `ActorContext.console()` and `false` (the existing `setLandlord(region, landlord, ctx)` with `false`).

**Behaviour:**

- Same steps as Task 2. `setTenant` holder is the landlord; `setTitleHolder` holder is the title holder, or the authority when there is none. `setLandlord` keeps its existing authority checks and adds the rollback, the "landlord unchanged" and `vacantOnly` conditions, and the diagnosis (`HOLDER_DIFFERS` → `NotAllowedToReassign` with the landlord now stored).
- `transferTitleHolder` is not changed.
- In `RealtyPaperApiImpl`, a `NotAuthorized` or `Occupied` backend result leaves WorldGuard, flags and signs untouched.
- Interim command mapping: `NotAuthorized` → `SET_NO_PERMISSION` (`UNSET_NO_PERMISSION` in unset, `TRANSFER_NO_PERMISSION` in `TransferCommand`), `Occupied` → `SET_JUST_RENTED` with the `region` placeholder.

- [ ] **Step 1: Write the failing test**

`GuardedHolderSetterTest`, same fixtures, each asserting result type and stored state:

```java
@Test void landlordSetsTenantOnVacantLease()
@Test void replacingATenantNeedsVacantOnlyOff()          // Occupied with true; Success with false
@Test void clearingATenantNeedsVacantOnlyOff()
@Test void tenantCannotClearThemselves()                 // context of the tenant, vacantOnly false: NotAuthorized
@Test void memberOfAnAccountLandlordSetsTenant()
@Test void rentedLeaseLandlordChangeIsOccupied()         // setLandlord with vacantOnly true
@Test void rentedLeaseLandlordChangesWhenNotVacantOnly()
@Test void formerTitleHolderCannotReassignFreehold()     // NotAuthorized; holder unchanged
@Test void titleHolderReassignsFreehold()
@Test void managerOfTheAuthorityAssignsAnUnsoldFreehold()
@Test void bypassActsForAnyone()
@Test void oldSignaturesStillApplyUnconditionally()
```

- [ ] **Step 2: Run it and confirm it fails**

- [ ] **Step 3: Implement the backend side; run the backend test until it passes**

- [ ] **Step 4: Add the `RealtyPaperApi` overloads and records; move each `RealtyPaperApiImpl` body onto its new overload**

- [ ] **Step 5: Add `RealtyPaperApiImplTest` cases: a backend `NotAuthorized` and `Occupied` from `setTenant` give the plugin-level result and never touch the owner list, flags or signs; update existing stubs to the backend signatures now called**

- [ ] **Step 6: Add the new `case` branches in the three commands; run the pass bar**

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(backend): a change of holder or tenant is refused when the caller no longer holds the region"
```

---

### Task 4: A term change redraws the region's signs

**Files:**
- Modify: `realty-paper/.../api/RealtyPaperApiImpl.java` (`setPrice`, `unsetPrice`, `setDuration`, `setMaxRenewals` guarded overloads)
- Test: `RealtyPaperApiImplTest.java`

**Reference:** commit `6623d43` (`writeTerms` helper and the `TermSetters` test group).

**Behaviour:**

- After a successful result, re-read the region's state and placeholders on the database executor, then call `signTextApplicator.updateLoadedSigns` on the main-thread executor, as `setTenant` does. The sign's state comes from the stored contract.
- A non-success result reads nothing and touches no sign.
- Flags are not re-applied.
- The future completes with the same backend result. It now completes on the main thread for success and refusal.

- [ ] **Step 1: Write failing tests**: successful `setPrice` and `setDuration` update loaded signs; `NotAuthorized` and `Occupied` do not; a rented lease's sign is redrawn as leased, not as for lease; flags are never applied.

- [ ] **Step 2: Run and confirm they fail**

- [ ] **Step 3: Implement one shared helper used by the four overloads**

- [ ] **Step 4: Run the tests, then the pass bar**

- [ ] **Step 5: Commit**

```bash
git commit -m "fix(set): a region's sign shows the new terms as soon as they change"
```

---

### Task 5: The router, and `set` term commands

**Files:**
- Create: `realty-paper/.../command/util/SetRouter.java`
- Modify: `realty-paper/.../command/SetCommandGroup.java`, `realty-paper/.../Realty.java` (construct one router, inject it)
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/command/util/SetRouterTest.java`, `realty-paper/src/test/java/io/github/md5sha256/realty/command/NowFlagPositionTest.java`

**Reference:** commits `c470ecc` and `bc86462` (`SetRouter`, `routeTerm`, `propose`, `reportWriteFailure`, the off-thread tests). The existing `PartyFlagPositionTest` on this branch shows a real Cloud parse test.

**Interfaces:**
- Consumes: `SetRouting` (Task 1); guarded `RealtyPaperApi` overloads (Task 3); `ActorContexts.forRegion`; `RealtyPaperApi.getFreeholdContract`, `getLeaseholdContract`, `proposeModification`.
- Produces:

```java
public record SetRouter(@NotNull RealtyPaperApi api, @NotNull ActorContexts actors,
                        @NotNull MessageContainer messages, @NotNull ExecutorState executorState,
                        @NotNull Logger logger) {

    /** Which ActorContext test "holds the region" uses. */
    public enum HolderTest { MANAGES, REASSIGNS }

    public record Routed(@NotNull SetRouting.Outcome outcome, @NotNull ActorContext actor,
                         @Nullable LeaseholdContractEntity lease) {
        /** For an ApplyNow outcome only; throws IllegalStateException otherwise. */
        public boolean vacantOnly();
    }

    public void route(@NotNull CommandSender sender, @NotNull WorldGuardRegion region,
                      @NotNull SetRouting.Kind kind, @NotNull HolderTest holderTest,
                      @NotNull String othersPermission, @NotNull String noPermissionKey,
                      boolean nowFlag, boolean unlimitedExtensions,
                      @NotNull Consumer<Routed> onRouted, @NotNull Party... extra);

    /** Sends {@code errorKey} with the error text to the sender and logs it. */
    public <T> @NotNull CompletableFuture<T> reportWriteFailure(@NotNull CompletableFuture<T> write,
                                                                @NotNull CommandSender sender,
                                                                @NotNull String errorKey);
}
```

- `SetCommandGroup` gains a `SetRouter router` component.

**Behaviour of `route`:**

- Read the sender's permissions before any async work: `bypass = hasPermission(othersPermission)`, `mayUseNow = hasPermission("realty.command.set.now")`. A non-player is `ActorContext.console()` with `mayUseNow = true`.
- On the database executor: build the context with `actors.forRegion(player, bypass, region, extra)` and read both contracts.
- On the main-thread executor: compute `tenure`, `holder`, `tenant`, `canPropose`; `holds` is `holder != null` and `actor.mayManage(holder)` or `actor.mayReassign(holder)` per `holderTest`; call `decide`; send the refusal or call `onRouted`.
- Refusal messages: `NOT_HOLDER` → `SET_NOT_LANDLORD` on a lease, `noPermissionKey` on a freehold; `NEEDS_NOW` → `SET_RENTED_NEEDS_NOW`; `NOW_NOT_PERMITTED` → `SET_RENTED_NO_NOW_PERMISSION`; `LEASE_ENDING` → `MODIFY_TERMINATING`; `UNLIMITED_NEEDS_NOW` → `SET_UNLIMITED_NEEDS_NOW`; `CONSOLE_NEEDS_NOW` → `SET_CONSOLE_NEEDS_NOW`.
- A failed read, or an exception while deciding or inside `onRouted`, is logged at SEVERE and sent to the sender as `SET_CHECK_PERMISSIONS_ERROR` with the `error` placeholder.

**Behaviour of `set price`, `set duration`, `set maxextensions`:**

- Each gains `--now`, switches its region argument to `RegionOrFlagParser.regionOrFlag()`, and routes with `Kind.TERM`, `HolderTest.MANAGES`, its own `.others` node and `MessageKeys.SET_NO_PERMISSION`. `unlimitedExtensions` is `maxExtensions < 0` for `set maxextensions`.
- `ApplyNow`: call the guarded setter with `routed.actor()` and `routed.vacantOnly()`. `Success` keeps today's message and events. `NotAuthorized` → the same message as `NOT_HOLDER`. `Occupied` → `SET_JUST_RENTED`. Other results keep today's messages. A failed future goes through `reportWriteFailure` with the command's existing `*_ERROR` key.
- `Schedule` and `Request`: fire `LeaseModifyProposeEvent`; if cancelled send `COMMON_ACTION_CANCELLED`. Then call `api.proposeModification(regionId, worldId, routed.actor(), …)` with only this command's term non-null. Messages and the `LeaseModificationProposedEvent` follow `ModifyCommandGroup.executePropose` on this branch, except `NotOccupied` → `SET_JUST_VACATED`. For the event's acting player, use the sender's id, or the landlord's when the sender is the console.
- `set price` fires `PriceSetEvent` only inside the routed callback, immediately before the write or the proposal, and only for a player.
- Remove the `.leasehold` permission strings. `authorizeLeaseholdSet`, `authorizeAsLandlord` and `LandlordGate` stay until Task 6 removes their last users.

- [ ] **Step 1: Write the failing tests**

`SetRouterTest` (Mockito, a controllable main-thread executor as in the reference):

```java
@Test void landlordOnVacantLeaseIsRoutedToApplyNow()            // ApplyNow(true); actor is the player's context
@Test void memberOfAnAccountLandlordIsRouted()                  // forRegion returns a context managing the account
@Test void adminOnRentedLeaseIsRoutedToSchedule()               // holds the .others node
@Test void tenantOnRentedLeaseIsRoutedToRequest()
@Test void consoleOnRentedLeaseWithNowAppliesUnguarded()        // ApplyNow(false)
@Test void consoleOnAnAccountsRentedLeaseIsToldToUseNow()       // SET_CONSOLE_NEEDS_NOW; no callback
@Test void strangerOnLeaseGetsNotLandlordAndNoCallback()
@Test void strangerOnFreeholdGetsTheSuppliedNoPermissionKey()
@Test void reassignTestUsesMayReassign()                        // manages but may not reassign → NOT_HOLDER
@Test void regionWithNoContractIsRoutedToApplyNow()
@Test void theCallbackWaitsForTheMainThread()                   // not called until the test executor runs
@Test void aRefusalWaitsForTheMainThread()
@Test void aCallbackThatThrowsIsReportedToTheSender()           // SET_CHECK_PERMISSIONS_ERROR
@Test void aFailedReadIsReportedToTheSender()
```

`NowFlagPositionTest` (a real Cloud manager, modelled on `PartyFlagPositionTest`): with `RegionOrFlagParser`, `set price 500 --now` yields the flag and no region; `set price 500 someregion --now` yields both; `set maxextensions -1 --now` yields `-1` and the flag; `set maxextensions -1` yields `-1` and no flag.

- [ ] **Step 2: Run and confirm they fail**

- [ ] **Step 3: Implement `SetRouter`; run `SetRouterTest` until it passes**

- [ ] **Step 4: Rework the three term handlers; construct and inject the router in `Realty.java`; run `NowFlagPositionTest`**

- [ ] **Step 5: Run the pass bar**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(set): a term change on a rented region waits for the next renewal unless --now is given"
```

---

### Task 6: Holder commands and `unset` use the same rules

**Files:**
- Modify: `SetCommandGroup.java` (`executeSetLandlord`, `executeSetTenant`, `executeSetTitleHolder`), `UnsetCommandGroup.java`, `Realty.java`
- Delete: `realty-paper/src/test/java/io/github/md5sha256/realty/command/SetCommandLandlordGateTest.java` (it tests `LandlordGate`, which this task removes; `reassignTestUsesMayReassign` in Task 5 covers the rule)
- Test: `SetRouterTest.java` (add cases)

**Reference:** commit `ab5b09c`.

**Interfaces:**
- Produces: `UnsetCommandGroup` gains a `SetRouter router` component, the same instance `SetCommandGroup` uses.

**Behaviour:**

- The six commands gain `--now`, use `RegionOrFlagParser.regionOrFlag()` for the region, and route with `Kind.HOLDER` and their own `.others` node. `set` commands pass `MessageKeys.SET_NO_PERMISSION`; `unset` commands pass `MessageKeys.UNSET_NO_PERMISSION`.
- `set landlord` routes with `HolderTest.REASSIGNS` and passes the new landlord as `extra`, as it does today. All others use `HolderTest.MANAGES`.
- Only `ApplyNow` reaches these handlers. Call the guarded overload with `routed.actor()`, and `routed.vacantOnly()` where the overload takes it: `setLandlord(region, newLandlord, actor, vacantOnly)`, `setTenant(region, Party.personal(id) or null, actor, vacantOnly)`, `setTitleHolder(region, Party.personal(id) or null, actor)`, `unsetPrice(regionId, worldId, actor)`.
- `NotAuthorized` → `SET_NOT_LANDLORD` for the lease commands, the command's no-permission key for the freehold commands. `Occupied` → `SET_JUST_RENTED`. `set landlord` keeps its `NotAllowedToReassign` and `NotAllowedToAssign` messages.
- Every write goes through `reportWriteFailure` with the command's existing `*_ERROR` key.
- `set titleholder` fires `TitleTransferEvent` inside the routed callback, before the write.
- Remove `authorizeLeaseholdSet`, `authorizeAsLandlord`, `LandlordGate`, `SET_LANDLORD_GATE` and every `getOwners()` check in both files. `set authority` is not touched.
- Update both class comments to describe the two rules and `--now`.

- [ ] **Step 1: Add router cases**

```java
@Test void tenantRunningUnsetTenantDoesNotHoldTheLease()        // Kind.HOLDER, --now, has set.now: SET_NOT_LANDLORD
@Test void landlordRunningUnsetTenantWithNowIsRouted()          // ApplyNow(false)
@Test void landlordRunningUnsetTenantWithoutNowIsToldToAddIt()  // SET_RENTED_NEEDS_NOW
@Test void titleHolderIsRoutedOnTheirFreehold()                 // ApplyNow(true)
@Test void managerOfTheAuthorityIsRoutedOnAnUnsoldFreehold()
```

- [ ] **Step 2: Run them.** They exercise Task 5's router and should pass on arrival; a failure is a Task 5 defect to fix first.

- [ ] **Step 3: Rework the three `set` holder handlers and the three `unset` handlers; inject the router; delete the removed helpers and the obsolete test**

- [ ] **Step 4: Confirm no WorldGuard owner check is left**

Run: `grep -n 'getOwners' realty-paper/src/main/java/io/github/md5sha256/realty/command/SetCommandGroup.java realty-paper/src/main/java/io/github/md5sha256/realty/command/UnsetCommandGroup.java`
Expected: no output.

- [ ] **Step 5: Run the pass bar**

- [ ] **Step 6: Commit**

```bash
git commit -m "fix(set): whoever acts for the landlord, not the tenant, decides who holds a lease"
```

---

### Task 7: Remove the `modify` term commands; permissions, help and the event's documentation

**Files:**
- Modify: `ModifyCommandGroup.java`, `paper-plugin.yml`, `messages.yml`, `MessageKeys.java`
- Modify: `realty-paper-api/.../api/event/PriceSetEvent.java` (documentation only)

**Reference:** commits `e60da4c` and `c52428d`.

**Behaviour:**

- `ModifyCommandGroup` loses `price`, `duration`, `maxextensions` and `executePropose`. `accept`, `reject`, `withdraw`, `inbox`, `outbox` are untouched. Remove only imports, components and helpers nothing in the file still uses; update `Realty.java` if a record component goes.
- `paper-plugin.yml`:
  - `realty.command.set.price`, `.set.duration`, `.set.maxextensions`, `realty.command.unset.price`: `default: true`.
  - Add `realty.command.set.now` beside the other `set` nodes, description `Allows --now on /realty set and /realty unset, changing a rented region immediately`, `default: op`.
  - Remove `realty.command.modify.price`, `.modify.duration`, `.modify.maxextensions`.
  - Reword each `set.*.others` and `unset.*.others` description that exists to `Allows using /realty <group> <sub> on regions you do not hold`. Leave every other node alone.
- `messages.yml` and `MessageKeys`: remove `set.occupied-use-modify`, `set.leasehold-no-permission` and `modify.not-occupied` with their constants.
- Help text: remove the `modify price|duration|maxextensions` line. `set price` becomes `/realty set price <price> <region> [--now]` with the description `Set freehold or lease price; a rented lease changes at the next renewal`. Add `[--now]` to the `set duration`, `set landlord`, `set tenant`, `unset tenant` lines. Add `/realty set maxextensions <count> <region> [--now]` with `Set lease extension limit (-1 for unlimited)` if no such line exists. Keep the existing line format and colour tags.
- `PriceSetEvent` documentation: it is called before a price is set, before a price change is scheduled for a lease's next renewal, and before a tenant's request is sent, so a listener must not assume the price changes at once.

- [ ] **Step 1: List what still refers to the things being removed**

Run: `grep -rnE 'SET_OCCUPIED_USE_MODIFY|SET_LEASEHOLD_NO_PERMISSION|MODIFY_NOT_OCCUPIED|modify\.(price|duration|maxextensions)|set\.[a-z]+\.leasehold' realty-paper/src realty-paper-adapters`
Expected: only the definitions and `ModifyCommandGroup`. Anything else is updated in this task.

- [ ] **Step 2: Make the changes**

- [ ] **Step 3: Run the same search again.** Expected: no output.

- [ ] **Step 4: Run the pass bar**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(set): players manage their own terms with set; modify keeps only the proposal inbox"
```

---

## In-game checks

These need a running server. Report each as passed, failed or not run.

1. A landlord without op sets price, duration and extensions on a vacant lease. Each applies at once and the sign shows the new price.
2. The same landlord sets a price on a rented lease. The reply says it applies at the next renewal, and the tenant pays the new price on renewal.
3. A member of a business does both of the above on a lease the business holds. A non-member is refused.
4. A tenant runs `set price` on the region they rent. It appears in the landlord's `modify inbox`.
5. An op runs `set price` on someone else's rented lease without `--now` (scheduled) and with `--now` (immediate), with and without naming the region.
6. The console runs `realty set price 500 <region>` on a rented lease with a player landlord (scheduled) and with an account landlord (told to use `--now`).
7. `unset tenant` by the landlord with `--now` works. By the tenant it is refused.
8. `set maxextensions -1` on a rented lease is refused without `--now`.
9. A seller cannot reprice a plot after it is sold; a stranger is refused on a freehold and on a lease.
10. `set landlord` and `set authority` with type flags still work as before.
