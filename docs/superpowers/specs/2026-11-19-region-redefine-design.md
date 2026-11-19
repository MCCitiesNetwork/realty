# Region Redefine — Design

Date: 2026-11-19
Status: Proposed.

Builds on `2026-11-18-region-rename-design.md` (the WorldGuard swap, the API
result and event pattern) and `2026-11-13-landlord-subregion-delete-design.md`
(the subregion definition and the landlord check).

## Purpose

A region's shape is fixed once it is registered. Today an operator can change
it only with WorldGuard's `/rg redefine`. That keeps Realty's records, because
Realty stores no geometry (WorldGuard holds the shape, and the web explorer
reads it live), but it skips every Realty rule: a subregion can be moved out of
its parent or over a sibling, a freehold can be shrunk past its subregions,
nobody is told, and the captured preview keeps showing the old area. Landlords
cannot do it at all.

This design adds a redefine that keeps everything attached to the region and
applies Realty's rules. Landlords redefine their own vacant subregions with the
subregion wand. Admins redefine any registered region with a WorldEdit
selection.

## Scope

A **subregion** is a registered leasehold whose WorldGuard parent is a
registered freehold, as in the subregion delete and the rename.

- A manager of a subregion's landlord party may redefine that subregion while
  it has no tenant, using the subregion wand.
- A holder of `realty.command.subregion.redefine.others` may redefine any
  subregion with the wand, rented or not.
- A holder of `realty.command.redefine` may redefine any registered region (a
  freehold, a standalone leasehold or a subregion) with a WorldEdit selection,
  rented or not.

A region not registered with Realty is not redefined by either command.

Why landlords get vacant subregions only: a tenant has paid for the space they
rent, and a landlord must not shrink or move it mid-lease. Admins may, to fix
mistakes, and the tenant is told.

Why landlords use the wand and admins use WorldEdit: landlords already draw
subregions with the wand and may not have WorldEdit. Admins create freeholds
with a WorldEdit selection in `/realty create`, so the redefine matches.

## Rules

These apply to every redefine, whoever runs it and whichever tool they use.
Only rules 1 and 2 depend on authority.

1. **Authority.** Without the bypass, the region must be a subregion and the
   actor must manage the lease's landlord
   (`ActorContext.mayManage(lease.landlord())`).
2. **Vacancy.** Without the bypass, the lease must have no tenant.
3. **The selection.** It must be a cuboid or a polygon in the region's world.
   A WorldEdit selection of another shape (an ellipsoid, a cylinder) is
   refused. The wand only makes these two shapes.
4. **The region.** It must be a cuboid or a polygon, the two shapes Realty
   creates (`WorldGuardRegionRename.isSupported`).
5. **No-op.** A selection with the same shape as the region (the same
   footprint points and Y range) is refused, as no-op role changes are.
6. **A subregion stays a valid subregion.** The new shape must lie fully inside
   the parent freehold, must not overlap a sibling subregion (the region itself
   is not its own sibling), and must have at least `subregion-min-volume`
   blocks. These are the checks the wand's create flow makes, from
   `SubregionSelectionValidator`.
7. **Children stay inside.** Every WorldGuard child region of the region (a
   freehold's subregions) must lie fully inside the new shape.
8. **Schematic capture.** A redefine is refused while a capture of the region
   is running.

Why rules 6 and 7 bind admins too: they are what make a subregion a subregion.
An admin who really wants to break them can still use `/rg redefine`.

## Commands

### `/realty subregion redefine <region>` (the wand)

1. The player marks the new footprint with the subregion wand, as for a new
   subregion: two points make a rectangle, three or more a polygon.
2. They run `/realty subregion redefine <region>`. Without a complete wand
   selection the reply is the existing `subregion.selection-incomplete`.
3. The height dialog opens, titled "Step 1 of 2: Height". If the selection has
   no height yet, the slider starts at the region's current height
   (`maxY - minY + 1`) in place of the default 16, so a footprint-only change is
   one click. The floor is where the corners were placed, as in the create
   flow.
4. A confirm dialog, "Step 2 of 2: Confirm", shows the region's name and the
   old and new size in blocks, with Confirm and Cancel.
5. Confirm calls `RealtyPaperApi.redefineRegion` with the wand selection's
   `toRegion()`. On success the wand selection is cleared.

The command is added to `SubregionCommandGroup`. `SubregionDialog` gains
`openRedefine(Player, WorldGuardRegion)`, which records the target in the
player's dialog state and opens the height dialog. The height dialog's
Continue goes to the confirm dialog when a redefine target is recorded and to
the create dialog otherwise. Clearing the selection also drops the target.

### `/realty redefine <region>` (WorldEdit)

The admin selects the new shape with WorldEdit and runs the command. It reads
the selection as `CreateCommand.getSelection` does; with no selection in the
player's world the reply is `redefine.no-selection`. There is no confirmation
step, as `/rg redefine` has none. The reply states the old and new size, so a
wrong selection is seen at once and is put right by redefining again. The
command is a new `RedefineCommand` record implementing `CustomCommandBean`,
registered like `RenameCommand`. It is player-only, because the console has no
selection.

### Permissions

| Node | Default | Grants |
|---|---|---|
| `realty.command.subregion.redefine` | op | Redefine a vacant subregion whose landlord you manage, with the wand |
| `realty.command.subregion.redefine.others` | op | Also redefine any subregion with the wand, rented or not |
| `realty.command.redefine` | op | Redefine any registered region with a WorldEdit selection |

Defaults follow the other `subregion` nodes: a server that hands landlords the
wand grants `subregion.redefine` with it. All three nodes are new, so nothing
changes for operators on upgrade. `/realty redefine` acts with the bypass
(`ActorContexts.forRegion(player, true, region)`); its node is the admin gate.

## Flow

```
redefineRegion(region, selection, ctx)                 main thread
  ├─ checks 3, 4, 5, 8
  └─ serializeByRegion(region):
       ├─ read the contracts; checks 1, 2               db thread
       ├─ checks 6, 7 against WorldGuard now            main thread
       ├─ fire RegionRedefineEvent (cancellable)
       ├─ WorldGuardRegionRedefine.redefine, save
       ├─ delete the region's schematic                 db thread
       └─ fire RegionRedefinedEvent                     main thread
the command replies and notifies the tenant or title holder
```

The whole redefine runs inside `serializeByRegion` for the region, so a `rent`
or `buy` of the same region through the API cannot slip between the vacancy
check and the swap. The geometry checks run on the main thread straight before
the swap, so a parent or sibling changed after the command started is seen.

### Step 1: checks on the main thread

In order: the region's shape (`UnsupportedRegion`), the selection's shape
(`UnsupportedSelection`), the selection's world (`WrongWorld`), the same shape
(`SameShape`), a capture in flight in `CaptureRegistry` (`CaptureRunning`).

### Step 2: the database

On the database executor, read the region's leasehold and freehold contracts
with `getLeaseholdContract` and `getFreeholdContract`. Neither: `NotRegistered`.
If the region has a WorldGuard parent, `isFreehold(parentId, worldId)` decides
whether it is a subregion. Without the bypass: not a subregion, or the actor
does not manage the landlord, `NotAuthorized`; a tenant, `Occupied`.

The step also takes the player to notify: the lease's `tenantId`, else the
freehold's `titleHolderId`.

No backend write is needed for authority: the shape lives only in WorldGuard,
and nothing in the database changes until the schematic delete.

### Step 3: geometry, the event and the swap

On the main thread:

1. If the region is a subregion: the selection's `blockVolume` is below
   `subregion-min-volume`, `TooSmall`; not fully inside the parent
   (`regionIsFullyContainedByParent`), `OutsideParent`; overlaps a sibling,
   `OverlapsSibling`. `overlappingSibling` gains an `exclude` parameter so the
   region does not overlap itself.
2. Build the replacement: a `ProtectedCuboidRegion` or
   `ProtectedPolygonalRegion` with the region's id and the selection's shape,
   using the existing `createProtectedRegion` in `RealtyPaperApiImpl`.
3. If a WorldGuard child of the region is not fully inside the replacement,
   `ChildOutside` with the first such child. A new
   `SubregionSelectionValidator.firstChildOutside(ProtectedRegion old,
   ProtectedRegion replacement, RegionManager)` converts each child to a
   WorldEdit region and reuses `regionIsFullyContainedByParent`.
4. Fire `RegionRedefineEvent`. Cancelled: `Cancelled`.
5. `WorldGuardRegionRedefine.redefine(region, replacement)`, a new class beside
   `WorldGuardRegionRename`:
   1. `replacement.copyFrom(old)`: owners, members, every flag, the priority and
      the parent. This is the method `/rg redefine` uses.
   2. `addRegion(replacement)`. It has the same id, so it replaces the old
      region in the manager.
   3. For every region whose parent is the old region, `setParent(replacement)`.
      Why: children hold the parent object, not its id, so without this they
      would keep pointing at the old shape. The rename does the same.
   4. `save()`. A failed save does not undo the redefine: the manager holds the
      replacement and WorldGuard writes it on its next save. The outcome is
      `RedefinedSaveFailed`.

Flags are copied, not applied again from the profile, so flags set by hand
survive. Signs are not redrawn: no sign placeholder shows a region's size.

### Step 4: the capture

On the database executor, the region's row in `RealtySchematic` is deleted.
Why: a buyer must not see a preview of a plot shaped differently from the one
on offer. A capture cannot be redone automatically, because its floor is the
block the capturing player stands on. The reply says when a capture was
removed, so the landlord knows to capture again.

A failure here does not undo the redefine; it is logged and the result carries
`captureRemoved = false`.

## Backend

New method on `RealtyBackend`:

```java
/**
 * Deletes the region's captured schematic.
 *
 * @return whether there was one
 */
boolean deleteSchematic(@NotNull String worldGuardRegionId, @NotNull UUID worldId);
```

New `RealtySchematicMapper.deleteByWorldGuardRegion(String, UUID)`, with the
neutral declaration and the `Maria…` statement, joining `RealtyRegion` as
`upsert` does. No migration and no table changes.

## Paper API

New method on `RealtyPaperApi`, additive:

```java
sealed interface RedefineRegionResult {
    /**
     * @param region         the region with its new shape
     * @param oldVolume      the blocks the old shape held
     * @param newVolume      the blocks the new shape holds
     * @param notify         the tenant of a leasehold, or the title holder of a freehold
     * @param captureRemoved whether a captured schematic was deleted
     */
    record Redefined(@NotNull WorldGuardRegion region, long oldVolume, long newVolume,
                     @NotNull Optional<UUID> notify, boolean captureRemoved)
            implements RedefineRegionResult {}
    /** Redefined, but WorldGuard could not save. It holds the change and writes it on its next save. */
    record WorldGuardSaveError(@NotNull Redefined redefined, @NotNull String error)
            implements RedefineRegionResult {}
    /** The selection is neither a cuboid nor a polygon. */
    record UnsupportedSelection() implements RedefineRegionResult {}
    record WrongWorld() implements RedefineRegionResult {}
    record SameShape() implements RedefineRegionResult {}
    record TooSmall(long volume, int minVolume) implements RedefineRegionResult {}
    record OutsideParent(@NotNull String parentId) implements RedefineRegionResult {}
    record OverlapsSibling(@NotNull String siblingId) implements RedefineRegionResult {}
    record ChildOutside(@NotNull String childId) implements RedefineRegionResult {}
    record NotRegistered() implements RedefineRegionResult {}
    record NotAuthorized() implements RedefineRegionResult {}
    /** Without a bypass, a rented subregion cannot be redefined. */
    record Occupied() implements RedefineRegionResult {}
    /** A listener cancelled the {@code RegionRedefineEvent}. */
    record Cancelled() implements RedefineRegionResult {}
    /** The WorldGuard region is neither a cuboid nor a polygon. */
    record UnsupportedRegion() implements RedefineRegionResult {}
    record CaptureRunning() implements RedefineRegionResult {}
    record Error(@NotNull String message) implements RedefineRegionResult {}
}

@NotNull CompletableFuture<RedefineRegionResult> redefineRegion(
        @NotNull WorldGuardRegion region, @NotNull Region selection, @NotNull ActorContext ctx);
```

`Region` is WorldEdit's, as in `quickCreateSubregion`. Volumes use
`SubregionSelectionValidator.blockVolume`, WorldGuard's own block count.

`RealtyPaperApiImpl` gains a `WorldGuardRegionRedefine` constructor parameter,
passed in from `Realty`.

### Events

Both extend `RealtyRegionEvent` and are fired by the API, as the rename events
are, so a plugin calling `redefineRegion` gets them too.

- `RegionRedefineEvent` (cancellable): the region with its current shape,
  `getNewRegion()` (the replacement, not yet in WorldGuard), and
  `Optional<UUID> getActorId()`.
- `RegionRedefinedEvent`: the region with its new shape, `getOldRegion()` (the
  replaced object, no longer in WorldGuard), and `Optional<UUID> getActorId()`.

The redefine event fires after the database checks, unlike the rename event,
because nothing the database decides can refuse a redefine after it.

## Messages

New keys under `redefine.*` in `messages.yml`, with constants in `MessageKeys`:

| Key | When |
|---|---|
| `success` | Redefined; `<region>`, `<old-volume>`, `<new-volume>` |
| `capture-removed` | Added to the reply when a capture was deleted; `<region>` |
| `worldguard-save-error` | Redefined, WorldGuard save failed; `<region>`, `<error>` |
| `no-selection` | `/realty redefine` with no WorldEdit selection in this world |
| `unsupported-selection` | The selection is not a cuboid or a polygon |
| `wrong-world` | The selection is in another world; `<region>` |
| `same-shape` | The selection is the region's current shape; `<region>` |
| `too-small` | `<volume>`, `<min-volume>` |
| `outside-parent` | `<region>`, `<parent>` |
| `overlaps-sibling` | `<region>`, `<sibling>` |
| `child-outside` | `<region>`, `<child>` |
| `not-registered` | `<region>` |
| `not-authorized` | Not a subregion whose landlord the caller manages; `<region>` |
| `occupied` | The subregion is rented; `<region>` |
| `unsupported-region` | `<region>` |
| `capture-running` | `<region>` |
| `error` | `<error>` |

Under `notification.*`: `region-redefined` (`<region>`), sent through
`RealtyNotificationEvent` to the tenant or title holder, unless they are the
actor. A cancelled event replies with `common.action-cancelled`.

The two dialogs' titles, body text and buttons are literal `Component`s, as the
existing `SubregionDialog` texts are. This sets aside the localisation rule for
dialog text, to keep one dialog flow consistent. Every chat reply goes through
`MessageContainer`.

## Conventions

- `commands`: Incendo Cloud with `CustomCommandBean`, as in the project (the
  playbook's Brigadier default is set aside). Three new nodes, `.others` for
  the bypass, a sealed result with one message per case.
- `database`: one new mapper statement and backend method; no migration.
- `threading`: contract reads and the schematic delete on `dbExec`;
  WorldGuard, the dialogs and events on the main thread; serialized per
  region; the chain ends in `exceptionally`, which logs and returns `Error`.
- `localisation`: replies through `MessageContainer`; dialog text as above.
- `testing`: see below.
- `release`: a new API method, two new events, a new backend method. All
  additive; next minor version.

## Error handling

- WorldGuard save fails after the swap: `worldguard-save-error`. The redefine
  stands.
- The schematic delete fails: logged at WARNING, the redefine stands, and no
  `capture-removed` line is sent. The old preview stays until the next capture.
- An unexpected exception: logged at SEVERE, reply `error`.

## Testing

Backend (Testcontainers MariaDB): `deleteSchematic` deletes the row and
returns true; returns false when there is none; leaves a same-named region's
schematic in another world.

Paper layer (unit tests, executors as `Runnable::run`, the backend faked):

- `WorldGuardRegionRedefine` against a real in-memory `RegionManager`: a cuboid
  becomes a polygon and a polygon a cuboid; owners, members, flags, priority
  and parent are kept; each child's `getParent()` is the replacement object
  (identity, not equality); the manager holds the replacement under the same
  id.
- `SubregionSelectionValidator`: `overlappingSibling` ignores the excluded
  region; `firstChildOutside` finds a child left outside and returns null when
  all fit, for cuboid and polygon children.
- `redefineRegion` in `RealtyPaperApiImplTest`: each refusal gives its result
  and makes no swap and no schematic delete; without bypass a rented subregion
  is `Occupied` and a freehold `NotAuthorized`; with bypass both are
  redefined; a cancelled event changes nothing; a success deletes the
  schematic after the swap and carries the tenant, or the title holder for a
  freehold.
- Every `redefine.*` key exists in `messages.yml`.

Live-server checks, listed in the plan:

1. As a landlord, redefine a vacant wand-made subregion; the height dialog
   starts at the current height.
2. As a landlord, try a rented subregion: `occupied`.
3. As an admin with the wand, redefine a rented subregion; the tenant is
   notified.
4. Try a footprint that crosses the parent's edge, and one over a sibling.
5. As an admin, shrink a freehold past a subregion with WorldEdit: refused.
   Grow it: `/rg info` shows the subregions still under it.
6. Redefine with a WorldEdit polygon selection.
7. Redefine a captured region: the web explorer shows the new outline and no
   preview.

## Known limitations

- **Top-level overlaps are not checked.** A freehold or standalone leasehold
  may be redefined over another region, as `/realty create` allows today.
- **A WorldGuard parent of a top-level region is not checked.** Rule 6 covers
  subregions only.
- **Only the tenant or title holder is notified.** An admin redefining a
  subregion does not notify its landlord, as the rename does not.
- **The capture is deleted, not kept.** Someone must capture the region again
  for the web explorer to show a preview.
- **A capture finishing in the gap is deleted.** A capture started straight
  after the swap and saved before the delete runs would be removed. It needs
  the capture to finish within milliseconds; the player captures again.
- **API callers outside the queue.** `serializeByRegion` orders the redefine
  against Realty's own API calls. A plugin that writes contracts through the
  backend directly is not ordered against it.
- **Plain `/rg redefine` still works** and still skips these rules. Realty does
  not watch for it.
- **The wand redefine draws a fresh footprint.** It does not load the region's
  current shape into the wand, because the wand can only add a point or undo
  the last one, so adjusting one corner of a polygon would mean redrawing it
  anyway.
