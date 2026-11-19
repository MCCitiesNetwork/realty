# Queued Region Re-read — Design

Date: 2026-11-19
Status: Approved.

Builds on `2026-11-19-region-redefine-design.md`, whose build added the first re-read, for
redefine only (`WorldGuardRegionRedefine.current`).

## Purpose

`RealtyPaperApiImpl` runs every write to a region in that region's queue (`serializeByRegion`), so
two operations on one region never interleave. Twelve of the queued operations take the
`WorldGuardRegion` their caller looked up before queuing: `buy`, `rent`, `unrent`, `extend`,
`terminate`, `payBid`, `payOffer`, `setTitleHolder`, `setTenant`, `setLandlord`, `renameRegion`
and `redefineRegion`. When the operation's turn comes it acts on that object.

A redefine replaces the region's `ProtectedRegion` with a new object under the same id. An
operation queued behind it still holds the old object, which WorldGuard no longer has:

- `buy` and `payBid`/`payOffer` set the buyer as owner of the old object, so in WorldGuard the
  buyer never owns the region.
- `rent`, `unrent` and the role setters change members or owners of the old object, with the same
  effect.
- `renameRegion` rebuilds the region from the old object's shape, which undoes the redefine.
- Flag and sign profiles are applied to the old object.

A rename or a delete ahead in the queue also leaves a stale object, but there the database step
already finds no region under the old name, so the operation refuses before touching WorldGuard.

The queued operations that take only an id (`redrawFromStore`, `writeAndShow`,
`finishPendingSale`) already look the region up when they run.

## Design

The queue looks the region up again when the operation's turn comes, and hands the operation the
region as WorldGuard has it then.

```java
/**
 * Runs {@code operation} in the region's queue with the region as WorldGuard has it when the
 * operation's turn comes. When WorldGuard no longer has the region, {@code gone} is the result and
 * the operation does not run. A world without a region manager has nothing to re-read, so the
 * region is passed as given.
 */
<T> @NotNull CompletableFuture<T> serializeRegion(
        @NotNull WorldGuardRegion region,
        @NotNull Supplier<T> gone,
        @NotNull Function<WorldGuardRegion, CompletableFuture<T>> operation);

/** The region as WorldGuard has it now; empty when it is gone. Main thread. */
@NotNull Optional<WorldGuardRegion> current(@NotNull WorldGuardRegion region);
```

Both are package-private on `RealtyPaperApiImpl`, so tests can call them. `serializeRegion` wraps
`serializeByRegion`: its operation first runs `current` on the main thread, then calls `operation`
with the result. `current` reads the region manager through
`WorldGuard.getInstance().getPlatform().getRegionContainer()`, as `updateChildLandlords` already
does.

**Threading.** WorldGuard's region tree is read on the main thread only. When the turn comes on
the main thread, which is always the case for an operation that did not have to wait, `current`
runs inline (`server.isPrimaryThread()`). Otherwise it hops to `mainThreadExec`. Why not always
hop: the main-thread executor runs on the next tick, which would delay every API call by up to
50 ms.

**Gone.** Each operation maps "the region is gone" to the result it already has for a region with
no contract, so no API type changes:

| Operation | Result when gone |
|---|---|
| `buy` | `BuyResult.NoFreeholdContract(regionId)` |
| `rent`, `unrent`, `extend`, `terminate` | their `NoLeaseholdContract(regionId)` |
| `payBid`, `payOffer` | their `NoPaymentRecord(regionId)` |
| `setTitleHolder` | `NoFreeholdContract(regionId)` |
| `setTenant`, `setLandlord` | `NoLeaseholdContract(regionId)` |
| `renameRegion`, `redefineRegion` | `NotRegistered()` |

**Each operation** takes its WorldGuard-facing region from the function argument instead of its
parameter. Reads made before queuing (the id, the world, argument checks) stay on the parameter:
the id and world cannot change under it, since a rename takes the queue of both names.

**Rename** holds two queues, taken in alphabetical order of the names. It re-reads the region
inside both, through `current`, not through `serializeRegion`.

**Redefine** moves from its own re-read to `serializeRegion`, and `WorldGuardRegionRedefine.current`
is deleted. Redefine's checks before queuing (shape, same shape, capture) still read the caller's
object; the shape checks against the surroundings run inside the queue on the current region, as
now.

## Conventions

- `threading`: the WorldGuard read is on the main thread, inline when already there.
- `testing`: the two methods are package-private so a test can hold the queue open, replace the
  region, and release it.
- `release`: no API change; it ships with the redefine stack.

## Testing

In `RealtyPaperApiImplTest`, with a real in-memory region manager:

- `serializeRegion` passes the region WorldGuard has when the turn comes: queue a first operation
  that returns an incomplete future, queue a second, replace the region in the manager, complete
  the first; the second receives the replacement.
- `serializeRegion` returns `gone` and does not run the operation when the region was removed.
- A world without a region manager passes the region as given.
- `buy` queued behind a redefine of the same region makes the buyer an owner of the region
  WorldGuard has.
- `renameRegion` queued behind a redefine keeps the new shape under the new name.
- The existing redefine tests for a stale and a removed region still pass, now through
  `serializeRegion`.

## Known limitations

- **Changes outside Realty's queue.** A region replaced by `/rg redefine` or by another plugin
  while a Realty operation runs is not seen. The queue only orders Realty's own operations.
- **Writers added after review.** `transferTitleHolder`, `createFreehold`, `createLeasehold`,
  `deleteRegion` and `quickCreateSubregion` (on the parent's queue) also take the queue now. A
  create was not safe outside it: a redefine refuses an unregistered region only by reading the
  database, and a create commits its row before it writes to WorldGuard. A create whose region is
  gone gives `Error`; a delete gives `NotRegistered`.
- **`deleteSubregion` takes the queue too**, so a redefine cannot put back a region the delete
  has just removed. Its rented path terminates the lease from inside the queue, through
  `terminateHoldingQueue`, because the queue is not reentrant and `terminate` would wait on itself.
- **`createAuction` is not queued.** It looks the region up again by id when it writes the flags,
  as `cancelAuction` and the id-based redraws do, so it writes to the region WorldGuard has.
- **The scheduler.** Its lease expiry and scheduled termination write outside the queue, but each
  looks the region up by id on the main thread when it writes, so against a redefine it writes to
  the region WorldGuard has: either the object a waiting redefine will copy from, or the region a
  redefine has put in place. Against a rename it does not hold: they read the id when they clear
  the rows and look the region up a tick later, so a rename in that gap makes the lookup miss, the
  region is skipped without a log, and the former tenant stays a WorldGuard owner with the flags
  and signs still showing the lease. The gap predates this change and is left for its own fix. Its sweep of subregions marked for deletion removes each WorldGuard region
  through `RealtyPaperApiImpl.removeSweptSubregion`, which waits its turn on the region, so a
  redefine that passed its checks before the sweep cannot put the region back after it.
- **Checks before queuing read the caller's object.** They read only the id, the world and, for
  redefine, the shape; the id and world cannot change, and a redefine that compares against a
  stale shape only risks refusing a no-op or letting one through.
- **Callers that keep a region.** A plugin holding a `WorldGuardRegion` across a redefine still
  holds the old object for its own use. Passing it to Realty's API is safe after this change.
