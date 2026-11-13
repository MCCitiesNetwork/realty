# Landlord Subregion Delete Implementation Plan

**Goal:** Let a manager of a subregion's landlord delete it: at once when vacant, or, when rented, by a landlord termination after which a sweep deletes it.
**Spec:** `docs/superpowers/specs/2026-11-13-landlord-subregion-delete-design.md`

Backend tests extend `AbstractDatabaseTest` and need a running Docker daemon (Testcontainers MariaDB).

## Task 1: Backend immediate delete of a vacant lease

**Files:**
- Modify `realty-backend-api/src/main/java/io/github/md5sha256/realty/api/RealtyBackend.java` (after `--- Delete ---`)
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/mapper/LeaseholdContractMapper.java`
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/mapper/MariaLeaseholdContractMapper.java`
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/RealtyBackendImpl.java` (after `deleteRegion`)
- Test: create `realty-backend/src/test/java/io/github/md5sha256/realty/database/SubregionDeleteTest.java`

**Interfaces:**

```java
sealed interface DeleteLeaseholdResult {
    record Success() implements DeleteLeaseholdResult {}
    record NoLeaseholdContract() implements DeleteLeaseholdResult {}
    record NotAuthorized() implements DeleteLeaseholdResult {}
    record Occupied(@Nullable LocalDateTime terminationEffectiveDate) implements DeleteLeaseholdResult {}
}
@NotNull DeleteLeaseholdResult deleteVacantLeasehold(@NotNull String worldGuardRegionId,
                                                     @NotNull UUID worldId,
                                                     @NotNull ActorContext ctx);
// mapper
int deleteVacantByRegion(@NotNull String worldGuardRegionId, @NotNull UUID worldId,
                         @Nullable Integer requiredLandlordPartyId);
```

- [ ] Write the failing tests in `SubregionDeleteTest`:
  - `vacantLeaseIsDeletedByLandlordManager`: create a lease with landlord A, delete as A, expect `Success`; `getLeaseholdContract` is null, and a count of `LeaseholdContract` rows for that contract id is 0 (raw JDBC through `database.openSession()`).
  - `rentedLeaseIsRefusedAsOccupied`: rent to B, delete as A, expect `Occupied(null)`, lease still there.
  - `terminatingLeaseReportsItsEffectiveDate`: rent, `terminateLease` with an effective date, delete, expect `Occupied(thatDate)`.
  - `nonManagerIsRefused`: delete as C, expect `NotAuthorized`, lease still there.
  - `bypassDeletesVacantButNotRented`: `ActorContext.player(C, true)`, vacant gives `Success`, rented gives `Occupied`.
  - `groupLandlordManagerMayDelete`: landlord `Party.account(...)`, actor `new ActorContext(C, Set.of(account), Set.of(), false)`, expect `Success`.
  - `freeholdIsNoLeaseholdContract`.
  - `tenantArrivingAfterTheReadMakesNothingDeleted`: call `deleteVacantByRegion` through the mapper on a rented lease with the landlord's party id; expect 0 rows.
- [ ] Run `./gradlew :realty-backend:test --tests "*SubregionDeleteTest"` and expect a compile FAIL: `deleteVacantLeasehold` does not exist.
- [ ] Implement:
  - The mapper `@Delete`: `DELETE lc FROM LeaseholdContract lc JOIN Contract c ... JOIN RealtyRegion rr ... WHERE region AND world AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId}) AND lc.tenantId IS NULL`.
  - `deleteVacantLeasehold`: read the lease (null → `NoLeaseholdContract`), `ctx.mayManage(lease.landlord())` or refuse, `requiredLandlordPartyId = ctx.bypass() ? null : namedPartyId(...)`, `endRead`. Then run the conditional delete. On 1 row, run `realtyRegionMapper().deleteByWorldGuardRegion` and commit. On 0, roll back, reread, and return `Occupied(lease.terminationEffectiveDate())` if it has a tenant, else `NotAuthorized`.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(backend): delete a vacant leasehold under the landlord check`.

## Task 2: Backend deletion mark and sweep

**Files:**
- Create `realty-backend/src/main/resources/sql/migrations/V22__lease_delete_when_vacant.sql`
- Modify `realty-backend/src/main/java/io/github/md5sha256/realty/database/maria/MariaSchemaMigrator.java` (step list)
- Modify the mapper pair and `RealtyBackendImpl` (mark, unmark, read, sweep, `clearTermination`)
- Modify `RealtyBackend.java`
- Test: extend `SubregionDeleteTest`

**Interfaces:**

```java
sealed interface MarkDeletionResult {
    record Success(@NotNull LocalDateTime effectiveDate) implements MarkDeletionResult {}
    record NoLeaseholdContract() implements MarkDeletionResult {}
    record NotAuthorized() implements MarkDeletionResult {}
    record NotTerminating() implements MarkDeletionResult {}
    record AlreadyMarked(@NotNull LocalDateTime effectiveDate) implements MarkDeletionResult {}
}
sealed interface UnmarkDeletionResult {
    record Success() implements UnmarkDeletionResult {}
    record NoLeaseholdContract() implements UnmarkDeletionResult {}
    record NotAuthorized() implements UnmarkDeletionResult {}
    record NotMarked() implements UnmarkDeletionResult {}
}
record DeletedLeasehold(@NotNull String worldGuardRegionId, @NotNull UUID worldId, @NotNull Party landlord) {}

@NotNull MarkDeletionResult markForDeletion(String worldGuardRegionId, UUID worldId, ActorContext ctx);
@NotNull UnmarkDeletionResult unmarkForDeletion(String worldGuardRegionId, UUID worldId, ActorContext ctx);
boolean isMarkedForDeletion(String worldGuardRegionId, UUID worldId);
@NotNull List<DeletedLeasehold> deleteMarkedVacantLeaseholds();
```

The mark is not added to `LeaseholdContractEntity`. That record is in the published `realty-backend-api` module, and a new component would break its constructor for other plugins. The mark is read through `isMarkedForDeletion` instead.

- [ ] Write the failing tests:
  - `markRefusedWhenNotTerminating`, `markRefusedForNonManager`.
  - `markClosesLeaseToTenants`: terminate, mark as A, expect `Success(date)`, `isMarkedForDeletion` true, `acceptingTenants` false.
  - `markTwiceReportsAlreadyMarked`.
  - `sweepDeletesMarkedLeaseOnceVacant`: terminate, mark, `clearTenant` through the mapper, then the sweep returns this region and the lease is gone.
  - `sweepLeavesMarkedRentedLease` and `sweepLeavesUnmarkedVacantLease`.
  - `cancelTerminationClearsMarkAndReopens`: terminate, mark, `cancelTermination` as A, then not marked and `acceptingTenants` true.
  - `unmarkReopensLease` and `unmarkRefusedWhenNotMarked`.
  - `migrationLeavesExistingLeasesUnmarked`: a lease created before any mark reads `isMarkedForDeletion` false.
- [ ] Run `./gradlew :realty-backend:test --tests "*SubregionDeleteTest"` and expect a compile FAIL: `markForDeletion` does not exist.
- [ ] Implement:
  - The migration: `ALTER TABLE LeaseholdContract ADD COLUMN deleteWhenVacant BOOLEAN NOT NULL DEFAULT FALSE;`, plus `new MigrationStep(22, "lease delete when vacant", "V22__lease_delete_when_vacant.sql")`.
  - Mapper `markDeleteWhenVacant(region, world, requiredLandlordPartyId)`: `SET deleteWhenVacant = TRUE, acceptingTenants = FALSE WHERE ... landlord guard AND terminationEffectiveDate IS NOT NULL AND deleteWhenVacant = FALSE`.
  - `unmarkDeleteWhenVacant(...)`: `SET deleteWhenVacant = FALSE, acceptingTenants = TRUE WHERE ... landlord guard AND deleteWhenVacant = TRUE`.
  - `selectDeleteWhenVacant(region, world)` returns `Boolean`.
  - `selectMarkedVacant()` returns region id, world id and landlord (as `PartySql` columns).
  - `deleteMarkedVacantById(leaseholdContractId)` with `deleteWhenVacant = TRUE AND tenantId IS NULL`.
  - `clearTermination` gains `acceptingTenants = CASE WHEN deleteWhenVacant THEN TRUE ELSE acceptingTenants END, deleteWhenVacant = FALSE` (the `CASE` first, because MariaDB applies `SET` from left to right).
  - The sweep: for each selected lease, in its own session, delete the lease row; if 1 row, delete the region and commit, then add it to the result.
- [ ] Run the same command and expect PASS. Also run `./gradlew :realty-backend:test --tests "*MariaSchemaMigratorTest"`.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(backend): mark a terminating lease for deletion and sweep it once vacant`.

## Task 3: Paper API deleteSubregion and cancelSubregionDelete

**Files:**
- Modify `realty-paper-api/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApi.java`
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/api/RealtyPaperApiImpl.java`
- Test: `realty-paper/src/test/java/io/github/md5sha256/realty/api/RealtyPaperApiImplTest.java`

**Interfaces:**

```java
sealed interface DeleteSubregionResult {
    record Deleted() implements DeleteSubregionResult {}
    /** Rented: deleted once the termination takes effect. {@code termination} is set when this call started it. */
    record Scheduled(@NotNull LocalDateTime effectiveDate, @Nullable TerminateResult.Success termination)
            implements DeleteSubregionResult {}
    record AlreadyScheduled(@NotNull LocalDateTime effectiveDate) implements DeleteSubregionResult {}
    record TerminatedNotMarked(@NotNull TerminateResult.Success termination) implements DeleteSubregionResult {}
    record TerminationFailed(@NotNull TerminateResult reason) implements DeleteSubregionResult {}
    record NotSubregion() implements DeleteSubregionResult {}
    record NotAuthorized() implements DeleteSubregionResult {}
    record WorldGuardSaveError(@NotNull String error) implements DeleteSubregionResult {}
    record Error(@NotNull String message) implements DeleteSubregionResult {}
}
@NotNull CompletableFuture<DeleteSubregionResult> deleteSubregion(@NotNull WorldGuardRegion region,
                                                                  @NotNull ActorContext ctx);

sealed interface CancelSubregionDeleteResult {
    record Success() implements CancelSubregionDeleteResult {}
    record NotScheduled() implements CancelSubregionDeleteResult {}
    record NotAuthorized() implements CancelSubregionDeleteResult {}
    record NotSubregion() implements CancelSubregionDeleteResult {}
}
@NotNull CompletableFuture<CancelSubregionDeleteResult> cancelSubregionDelete(@NotNull WorldGuardRegion region,
                                                                              @NotNull ActorContext ctx);

/** Removes a deleted subregion's WorldGuard region and flags. Main thread. */
void removeDeletedSubregion(@NotNull World world, @NotNull String worldGuardRegionId);
```

The methods take an `ActorContext`, as `terminate` does. The command builds it with `ActorContexts.forRegion`.

- [ ] Write the failing tests:
  - `deleteSubregionRefusesRegionWithoutParent` and `deleteSubregionRefusesParentThatIsNotFreehold` (stub `getFreeholdContract` to null): expect `NotSubregion`, and the backend delete is never called.
  - `vacantSubregionIsDeletedAndWorldGuardRegionRemoved`: give the region a parent, stub the freehold and `deleteVacantLeasehold → Success`, stub `regionContainer.get(any())` with a mocked `RegionManager`; expect `Deleted` and `removeRegion(REGION_ID)` and `save()` were called.
  - `rentedSubregionStartsTerminationThenMarks`: `deleteVacantLeasehold → Occupied(null)`, the lease rented to a tenant, landlord ctx; expect `Scheduled` with a non-null termination, and `terminateLease` then `markForDeletion` in that order.
  - `terminatingSubregionOnlyMarks`: `Occupied(date)`; expect `Scheduled(date, null)`, and `terminateLease` never called.
  - `failedTerminationLeavesNoMark`: the termination is refused (`NotAuthorized` from the plan); expect `TerminationFailed`, and `markForDeletion` never called.
  - `refusedMarkAfterTerminationIsReported`: `markForDeletion → NotAuthorized` after a successful termination; expect `TerminatedNotMarked`.
  - `alreadyMarkedIsReported`: `Occupied(date)`, mark returns `AlreadyMarked(date)`; expect `AlreadyScheduled(date)`.
  - `cancelSubregionDeleteMapsBackendResults`.
- [ ] Run `./gradlew :realty-paper:test --tests "*RealtyPaperApiImplTest"` and expect a compile FAIL: `deleteSubregion` does not exist.
- [ ] Implement:
  - The parent check: `region.region().getParent()`, null → `NotSubregion`, else `getFreeholdContract(parentId, worldId)` on `dbExec`, null → `NotSubregion`.
  - On `Success`: `removeDeletedSubregion` on the main thread, as `deleteRegion` does with WorldGuard (`StorageException` → `WorldGuardSaveError`).
  - On `Occupied(null)`: compose `terminate(region, ctx, false)`. A non-`Success` becomes `TerminationFailed(result)`; else mark.
  - On `Occupied(date)`: mark only.
  - The mark results map to `Scheduled`, `AlreadyScheduled` or `TerminatedNotMarked`.
  - `exceptionally` → `Error`.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Commit `feat(api): delete a subregion now or when its lease ends`.

## Task 4: Command, permissions, messages and the sweep

**Files:**
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/SubregionCommandGroup.java`
- Modify the place `SubregionCommandGroup` is constructed in `Realty.java`, to pass the new dependencies
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/Realty.java` (`scheduleTasks`: the sweep after `clearTerminatedLeaseholds`)
- Modify `realty-paper/src/main/java/io/github/md5sha256/realty/command/TerminateCommand.java` (`executeCancel`: tell the landlord when a mark was removed)
- Modify `realty-paper/src/main/resources/paper-plugin.yml`, `messages.yml`, and `localisation/MessageKeys.java`
- Test: create `realty-paper/src/test/java/io/github/md5sha256/realty/command/SubregionDeleteMessagesTest.java`

**Interfaces:** the `subregion.delete.*`, `notification.subregion-deleted`, `notification.termination-subregion-removed` and `terminate.cancel-delete-mark` keys.

- [ ] Write the failing test `SubregionDeleteMessagesTest`: load the bundled `messages.yml` as `RegionNotificationListenerTest` does, and assert that each new `MessageKeys` constant resolves to a non-blank entry.
- [ ] Run `./gradlew :realty-paper:test --tests "*SubregionDeleteMessagesTest"` and expect a compile FAIL: the constants do not exist.
- [ ] Implement:
  - `delete <region> [--confirm]` and `delete cancel <region>` on `SubregionCommandGroup`, both players only. Bypass comes from `realty.command.subregion.delete.others`.
  - Without `--confirm`, the command reads the lease and shows `confirm-vacant` or `confirm-rented`. The rented prompt shows the existing termination date, or now plus `terminationNoticeSeconds`. The link runs `/realty subregion delete <region> --confirm`.
  - With `--confirm`, it fires `RegionDeleteEvent` and calls `deleteSubregion`. On `Deleted` it fires `RegionDeletedEvent`. On a `Scheduled` that carries a termination, it fires `LeaseTerminationScheduledEvent` as `TerminateCommand` does. On any `Scheduled`, it sends the tenant `notification.termination-subregion-removed` through `RealtyNotificationEvent`. `TerminationFailed` reuses the `terminate.*` messages through a helper shared with `TerminateCommand`.
  - The sweep in `scheduleTasks` runs `logic.deleteMarkedVacantLeaseholds()` off the main thread. On the main thread it calls `removeDeletedSubregion` and notifies the landlord party (`notification.subregion-deleted`).
  - `executeCancel` reads `isMarkedForDeletion` before cancelling and appends `terminate.cancel-delete-mark` on success when it was marked.
  - Declare both nodes in `paper-plugin.yml` with `default: op`.
- [ ] Run the same command and expect PASS.
- [ ] Run `./gradlew build`.
- [ ] Live-server checks (by hand on a test server with `lease-termination-notice-seconds: 60`):
  1. Wand-create a subregion. Run `/realty subregion delete <it>`: the prompt shows. Click confirm: deleted, and `/rg info` finds no region.
  2. Create another, rent it from a second account, and delete with confirm: the tenant gets the termination notice and the removal line. After about two minutes, the region is gone and the landlord is notified.
  3. Schedule again, then run `/realty terminate cancel`: the reply mentions the removed mark, and the plot stays and is rentable after the tenant leaves.
  4. Schedule again, then run `/realty subregion delete cancel`: the termination still ends the lease, and the plot stays.
  5. A third account without management gets `not-authorized`. A standalone `/realty create leasehold` region gets `not-subregion`.
- [ ] Commit `feat(command): /realty subregion delete and the deletion sweep`.

There is no `CLAUDE.md` in this repository to update.
