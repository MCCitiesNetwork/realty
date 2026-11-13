# Landlord Subregion Delete — Design

Date: 2026-11-13
Status: Draft, awaiting review

Builds on `2026-10-05-set-command-authority-design.md` (the authority rule and
the conditional-write pattern) and the subregion wand.

## Purpose

A landlord who carved a subregion out of their freehold with the wand has no
way to remove it again. Only `/realty delete` can, and that is op-only and
checks neither the landlord nor the tenant. This design lets a landlord delete
their own subregion: at once when nobody is renting it, or automatically when
the lease ends if someone is.

## Scope

A **subregion** is a registered leasehold whose WorldGuard region has a parent,
and that parent is a registered freehold. Every region the wand makes has this
shape (`quickCreateSubregion` sets the parent).

Only subregions can be deleted this way. A standalone leasehold an admin made
with `/realty create leasehold` stays admin-only through `/realty delete`.

Why: deleting a subregion removes a WorldGuard region that Realty created, and
returns the space to the freehold the landlord's party already holds. The
landlord can make a new one with the wand at any time. A standalone leasehold's
WorldGuard region was made by an admin and can carry flags, members or child
regions the landlord had no part in. Only an admin can register the plot again.
If landlords are ever allowed to create standalone leaseholds themselves, this
reason no longer holds.

## Rules

1. **Authority.** Any manager of the lease's landlord party may delete. This is
   the rule `set` uses: `ActorContext.mayManage(lease.landlord())`. It covers
   personal landlords, groups and accounts.
2. **Vacant: delete now.** A subregion with no tenant is deleted at once.
3. **Rented: delete when the lease ends.** A subregion with a tenant is never
   deleted at once, not even by a bypassing actor. Instead the command
   schedules the delete:
   - If the lease is not terminating, it schedules an ordinary landlord
     termination with notice, through the same path as `/realty terminate`
     without `--now`. Then it marks the lease for deletion.
   - If the lease is already terminating (started by either side), it only
     adds the mark. That termination's date stands.
   - Once the termination clears the tenant, the region is deleted (see
     Scheduled deletes).
4. **Bypass.** `realty.command.subregion.delete.others` skips rule 1 only. It is
   for staff cleaning up a subregion whose landlord is gone.
5. **Confirmation.** The first run shows what will happen (deleted now, or the
   date the termination takes effect and the delete follows) and a link that
   runs the same command with `--confirm`. Only the confirmed run acts. This is
   the pattern `/realty buy` uses for fees.

Why the scheduled delete is a termination: telling the landlord to come back
after the tenant leaves does not work, because they forget. A separate "no
more extensions" rule would undercut the termination notice period
(`lease-termination-notice-seconds`): a lease whose paid term ends tomorrow
would give one day's notice. Termination already guarantees the notice,
extends the lease to cover it, refunds unused time, and refuses renew, unrent
and modification proposals while it runs. Reusing it keeps one rule for how a
lease ends, and the delete always follows that rule.

## Command

```
/realty subregion delete <region> [--confirm]
/realty subregion delete cancel <region>
```

`cancel` removes the deletion mark only, under the same nodes and authority
rule. The reply says the termination still stands and links
`/realty terminate cancel <region>`. Why not cancel both: the termination may
have been started by the tenant or before the delete was scheduled, so undoing
it is a separate choice.

A region named `cancel` cannot be deleted with the command. WorldGuard allows
that name, but it is not worth a different syntax.

| Node | Default | Grants |
|---|---|---|
| `realty.command.subregion.delete` | op | Delete a vacant subregion whose landlord you manage |
| `realty.command.subregion.delete.others` | op | Also delete one whose landlord you do not manage |

Both default to op, like the other `subregion` nodes: a server that hands
landlords the wand grants this node with it. The bypass uses `.others`, like
`terminate`, `modify`, `add` and `remove`, and not `.bypass` like
`subregion.confirm.bypass`, because it means "act on a region you do not
control". Both are new nodes, so nothing is renamed.

The command is for players only. The console uses `/realty delete`.

## Flow

```
SubregionCommandGroup.executeDelete            main thread
  ├─ resolve the WorldGuard region; check its parent is a registered freehold
  ├─ fire RegionDeleteEvent (cancellable)
  ├─ no --confirm → show the summary and the confirm link, stop
  └─ api.deleteSubregion(region, actorContext)
        RealtyPaperApiImpl.deleteSubregion
          ├─ actorContext(player, bypass, region)     db thread
          ├─ realtyApi.deleteVacantLeasehold(...)     db thread
          └─ on Success:                              main thread
               remove the WorldGuard region, save, clear profile flags
        fire RegionDeletedEvent, send the result message
```

The parent check runs again in `deleteSubregion` before any database write,
because an API caller may skip the command.

`deleteSubregion` tries the vacant delete first. On `Occupied` it schedules
instead:

```
deleteSubregion, on Occupied
  ├─ lease not terminating → terminate(region, ctx, immediate = false)
  │     (serialised per region, notice and tenant notifications as today)
  │     on failure → return that failure, no mark
  └─ realtyApi.markForDeletion(...)          db thread
        → Scheduled(effectiveDate)
```

If the termination succeeds but the mark is refused (the landlord changed in
between), the result is `TerminatedNotMarked`. The landlord is told that the
lease is terminating but the delete was not scheduled. The termination is not
undone, because the tenant has already been notified of it.

## Scheduled deletes

**Storage.** Migration `V22__lease_delete_when_vacant.sql` adds
`deleteWhenVacant BOOLEAN NOT NULL DEFAULT FALSE` to `LeaseholdContract`.
Existing rows read `FALSE`, so nothing changes on upgrade. The mark is read
through `isMarkedForDeletion` and is not added to `LeaseholdContractEntity`.
That record is in the published `realty-backend-api` module, and a new
component would break its constructor for other plugins.

**Marking.** `markForDeletion` is a conditional update: it sets
`deleteWhenVacant = TRUE` and `acceptingTenants = FALSE` where the landlord
matches (unless bypassing) and `terminationEffectiveDate IS NOT NULL`. Closing
the lease to tenants covers the case where the tenant leaves early: nobody can
rent the plot in the gap before the sweep.

**Sweep.** The one-minute task in `Realty.scheduleTasks` gains a step after
`clearExpiredLeaseholds` and `clearTerminatedLeaseholds`. It calls
`realtyApi.deleteMarkedVacantLeaseholds()`, which selects leases with
`deleteWhenVacant = TRUE AND tenantId IS NULL` and deletes each one with the
same two statements as the immediate delete, in its own transaction. Here the
lease delete's conditions are `deleteWhenVacant = TRUE AND tenantId IS NULL`
in place of the landlord check. For each region deleted, the main thread
removes the WorldGuard region, clears its flags and notifies the landlord
(`notification.subregion-deleted`). The sweep only looks for marked leases
with no tenant, so it deletes whichever way the tenant left: the termination
taking effect, or something an admin did.

The sweep does not check that the WorldGuard parent is still a freehold. That
was checked when the mark was set.

**Cancelling the termination.** `cancelTermination` also sets
`deleteWhenVacant = FALSE` and puts `acceptingTenants` back to `TRUE`, in the
same update. Otherwise the plot would be deleted without warning whenever the
tenant left later. The cancel reply tells the landlord when it removed a mark.

**Cancelling the delete.** `unmarkForDeletion` sets `deleteWhenVacant = FALSE`
and `acceptingTenants = TRUE`, under the same authority conditions as marking.

**Tenant notice.** The tenant gets the usual
`notification.termination-scheduled-tenant`, followed by
`notification.termination-subregion-removed`, which says the plot will be
removed when the lease ends. If the lease was already terminating, the tenant
gets only the second message.

## Backend

New method on `RealtyBackend`:

```java
sealed interface DeleteLeaseholdResult {
    record Success() implements DeleteLeaseholdResult {}
    record NoLeaseholdContract() implements DeleteLeaseholdResult {}
    record NotAuthorized() implements DeleteLeaseholdResult {}
    /** The lease has a tenant; {@code terminationEffectiveDate} is set if it is being terminated. */
    record Occupied(@Nullable LocalDateTime terminationEffectiveDate) implements DeleteLeaseholdResult {}
}

@NotNull DeleteLeaseholdResult deleteVacantLeasehold(@NotNull String worldGuardRegionId,
                                                     @NotNull UUID worldId,
                                                     @NotNull ActorContext ctx);
```

It follows `setTenant`. It reads the lease and returns `NoLeaseholdContract` or
`NotAuthorized` from that read. Then, in one transaction, it runs two
statements.

First, a conditional delete of the lease row:

```sql
DELETE lc FROM LeaseholdContract lc
INNER JOIN Contract c ON c.contractId = lc.leaseholdContractId AND c.contractType = 'leasehold'
INNER JOIN RealtyRegion rr ON rr.realtyRegionId = c.realtyRegionId
WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
  AND rr.worldId = #{worldId}
  AND (#{requiredLandlordPartyId} IS NULL OR lc.landlordPartyId = #{requiredLandlordPartyId})
  AND lc.tenantId IS NULL
```

If that removes one row, it then runs the existing
`realtyRegionMapper().deleteByWorldGuardRegion` and commits.

The landlord and vacancy conditions are part of the delete statement, so a
`rent` or `set landlord` that commits between the read and the delete makes it
remove nothing. A `rent` waiting on the row lock then finds no lease. When no
row is deleted, the method rolls back, reads the lease again (as
`diagnoseLeaseholdRefusal` does) and returns `NotAuthorized` or `Occupied`.

Why two statements: `LeaseholdContract` has no foreign key to `Contract` or
`RealtyRegion`. Deleting only the region cascades the `Contract` link row, the
sign rows and the other region-keyed tables, but it leaves the lease row
behind. `MariaPartyMapper` counts lease rows by landlord with no join, so
leftover rows would inflate a party's counts. `LeaseholdModification` cascades
from the lease row. A single multi-table `DELETE` is not used, because MariaDB
does not guarantee the order in which it deletes from the tables, and the
foreign keys need an order.

No history row is written, as `deleteRegion` writes none. `LeaseholdHistory`
is keyed by region name with no foreign key, so earlier rows stay. If the
landlord makes a new subregion with the same name, its history shows the old
lease's rows too.

`deleteRegion`'s bid and offer refund handling is not needed. It concerns
freehold sales, and a leasehold region has no freehold contract.

## Paper API

New method on `RealtyPaperApi` (additive, a minor version):

```java
sealed interface DeleteSubregionResult {
    record Deleted() implements DeleteSubregionResult {}
    record NotSubregion() implements DeleteSubregionResult {}
    record NotAuthorized() implements DeleteSubregionResult {}
    /**
     * Rented: the delete happens once the termination takes effect at {@code effectiveDate}.
     * {@code termination} is set when this call started the termination, so the caller can
     * fire the usual termination event.
     */
    record Scheduled(@NotNull LocalDateTime effectiveDate, @Nullable TerminateResult.Success termination)
            implements DeleteSubregionResult {}
    record AlreadyScheduled(@NotNull LocalDateTime effectiveDate) implements DeleteSubregionResult {}
    /** The termination went through but the mark was refused. */
    record TerminatedNotMarked(@NotNull TerminateResult.Success termination) implements DeleteSubregionResult {}
    /** The termination could not be scheduled; carries the reason {@code terminate} gave. */
    record TerminationFailed(@NotNull TerminateResult reason) implements DeleteSubregionResult {}
    record WorldGuardSaveError(@NotNull String error) implements DeleteSubregionResult {}
    record Error(@NotNull String message) implements DeleteSubregionResult {}
}

@NotNull CompletableFuture<DeleteSubregionResult> deleteSubregion(
        @NotNull WorldGuardRegion region, @NotNull ActorContext ctx);
```

It takes an `ActorContext`, as `terminate` does. The command builds it with
`ActorContexts.forRegion`, honouring the `.others` bypass.

Also `cancelSubregionDelete(region, ctx)`, returning `Success`,
`NotScheduled`, `NotAuthorized` or `NotSubregion`.

`NoLeaseholdContract` from the backend becomes `NotSubregion`. On `Success` it
always removes the WorldGuard region (no `includeworldguard` choice): leaving
it would leave a protected area still owned by the former landlord. Then it
calls `regionProfileService.clearAllFlags`, the same steps as `deleteRegion`
with `includeWorldGuard = true`.

## Messages

New keys under `subregion.delete.*` in `messages.yml`, with constants in
`MessageKeys`:

| Key | When |
|---|---|
| `confirm-vacant` | First run, vacant: names the region and its parent, with the confirm link |
| `confirm-rented` | First run, rented: the date the termination will take effect (now plus notice, or the existing date), with the confirm link |
| `success` | Deleted |
| `scheduled` | Rented: termination started; `<date>` |
| `scheduled-existing` | Rented and already terminating: mark added; `<date>` |
| `already-scheduled` | Already marked; `<date>` |
| `terminated-not-marked` | Termination went through, mark refused; `<date>` |
| `cancelled` | Mark removed; links `/realty terminate cancel` |
| `not-scheduled` | `cancel` on an unmarked lease |
| `not-subregion` | Not a leasehold, or its parent is not a registered freehold |
| `not-authorized` | Caller does not manage the landlord |
| `worldguard-save-error` | Database row gone, WorldGuard save failed; `<error>` |
| `error` | Anything else; `<error>` |

A `TerminationFailed` result reuses the existing `terminate.*` message for its
reason. Under `notification.*`: `subregion-deleted` (to the landlord, from the
sweep) and `termination-subregion-removed` (to the tenant). Under
`terminate.*`: `cancelled-delete-mark`, added to the cancel reply when a mark
was removed. The help list has no subregion commands today, so none are added.

## Conventions

- `commands`: Cloud subcommand on `SubregionCommandGroup`, `--confirm` flag,
  two declared nodes.
- `database`: migration V22 adds one column; new leasehold mapper statements
  for the conditional delete, mark, unmark and the sweep's select. Each delete
  runs in a transaction with the existing region delete.
- `threading`: database work on `dbExec`, WorldGuard and events on the main
  thread, as `deleteRegion` does.
- `localisation`: every reply through `MessageContainer`.
- `testing`: see below.
- `release`: new API methods are additive; ship in the next minor.

## Error handling

- WorldGuard save fails after the database delete: report
  `worldguard-save-error`. The Realty registration is gone and the WorldGuard
  region is still there, which is how `deleteRegion` behaves today. An admin can
  remove it with `/rg remove`.
- An unexpected exception: log it and report `error`, as `deleteRegion` does.
- A cancelled `RegionDeleteEvent`: report `common.action-cancelled`.

## Testing

Backend (`RealtyBackendImplTest` style, Testcontainers MariaDB):

- A vacant lease and a manager of the landlord: deleted, and the
  `RealtyRegion`, `Contract` and `LeaseholdContract` rows are all gone.
- A tenant: `Occupied(null)`, and nothing deleted.
- A terminating lease with a tenant: `Occupied(date)`.
- Mark: refused when not terminating, or when the landlord differs; sets
  `acceptingTenants = FALSE`.
- Sweep: a marked lease whose tenant is cleared is deleted. A marked lease
  still rented is not. An unmarked vacant lease is not.
- `cancelTermination` on a marked lease clears the mark and reopens the lease.
- Unmark restores `acceptingTenants = TRUE`.
- Migration V22: an existing lease reads `deleteWhenVacant = FALSE`.
- A caller who does not manage the landlord: `NotAuthorized`.
- Bypass with no management: deleted when vacant, `Occupied` when let.
- A group landlord with a member manager: deleted.
- No lease (a freehold region): `NoLeaseholdContract`.
- The race: a tenant is set between the read and the delete (via a test hook
  or by calling the mapper directly), and the result is `Occupied`, with
  nothing deleted.

Paper layer: a unit test for the subregion check (parent missing, parent not
a registered freehold, parent a freehold). Live-server checks in the plan:
create with the wand, delete with and without `--confirm`, rent and then try to
delete, and confirm the WorldGuard region is gone. Then the scheduled path:
schedule a delete on a rented subregion with a short notice setting, wait for
the termination, and check that the sweep removes it. Also cancel a
termination and check that the plot stays.

Paper layer: `deleteSubregion` on `Occupied` calls `terminate` only when the
lease is not terminating, and a failed `terminate` leaves no mark (with the
backend faked).

## Known limitations

- **Signs.** The sign rows cascade with the region, but the sign blocks keep
  their text and the in-memory `SignCache` keeps its entries until restart, as
  with `/realty delete` today. Cleaning these up on delete is a separate change
  for both commands.
- **`/realty delete` leaves lease and freehold rows behind.** The existing
  admin delete has the gap described under Backend, for both lease and
  freehold rows. This design does not change `deleteRegion`. Fixing it, and
  removing the rows already left behind, is a separate change.
- **Child regions of the subregion.** If someone has made WorldGuard regions
  inside the subregion by hand, WorldGuard's default removal unsets their
  parent and keeps them. The wand does not make such regions.
- **A tenant's own termination counts.** If the tenant gave notice and the
  landlord then marks the lease, the delete follows the tenant's date. That is
  intended: the plot is going vacant then anyway.
- **Up to a minute's delay.** The sweep runs once a minute, so a scheduled
  delete happens up to a minute after the termination clears the tenant.
- **No undo.** The `--confirm` step is the only safeguard. A deleted subregion
  is recreated with the wand at the same price and terms the landlord types
  again; its lease history is not kept.
- **The parent can change.** The subregion check reads the WorldGuard parent
  when the command runs. Changing the parent by hand with `/rg setparent` can
  make a standalone leasehold count as a subregion. Only admins can run that.
