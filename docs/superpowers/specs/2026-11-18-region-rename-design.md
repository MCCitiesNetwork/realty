# Region Rename — Design

Date: 2026-11-18
Status: Draft, awaiting review

Builds on `2026-10-05-set-command-authority-design.md` (the authority rule and
the conditional-write pattern) and `2026-11-13-landlord-subregion-delete-design.md`
(the subregion check and the database-then-WorldGuard order).

## Purpose

A region's name is fixed once it is registered. To change it, an admin must
delete the region and register it again, which loses its contracts, signs and
history. This design adds a real rename: the region keeps everything and only
its name changes. A landlord can rename their own subregions. An admin can
rename any region.

## Scope

A **subregion** is a registered leasehold whose WorldGuard region has a parent,
and that parent is a registered freehold. This is the definition the subregion
delete uses.

- A manager of a subregion's landlord party may rename that subregion.
- A holder of the bypass node may rename any registered region: a freehold, a
  standalone leasehold, or a subregion whose landlord they do not manage.
- The console may rename any registered region.

A region that is not registered with Realty is not renamed by this command.

Why landlords get subregions only: a subregion's name was chosen by the
landlord in the wand dialog, so letting them change it adds no new power. A
freehold's or standalone leasehold's name was chosen by an admin and can be
named in server configuration (see Known limitations). If landlords are ever
allowed to name standalone regions themselves, this reason no longer holds.

## Rules

1. **Authority.** Without the bypass, the region must be a subregion and the
   actor must manage the lease's landlord party
   (`ActorContext.mayManage(lease.landlord())`). This covers personal
   landlords, groups and accounts.
2. **Bypass.** `realty.command.rename.others` skips rule 1.
3. **Rented regions.** A rename is allowed while the region has a tenant. The
   tenant is notified with the old and the new name. Why: the name gives the
   tenant no right, and waiting for a vacancy would stop a landlord fixing a
   typo for the length of a lease.
4. **The new name.** It must match the pattern the create commands use,
   `^[A-Za-z0-9-]+$`. WorldGuard lower-cases region ids, so the new name is
   lower-cased before anything is stored. The new name must differ from the
   current one after lower-casing, and no region with that name may exist in
   the world's WorldGuard region manager or in Realty.
5. **No confirmation step.** A rename is undone by renaming back.
6. **Schematic capture.** A rename is refused while a schematic capture of the
   region is running. Why: the capture saves under the name it started with.

## Command

```
/realty rename <region> <new-name>
```

| Node | Default | Grants |
|---|---|---|
| `realty.command.rename` | op | Rename a subregion whose landlord you manage |
| `realty.command.rename.others` | op | Also rename any other registered region |

Both default to op, like the `subregion` nodes: a server that hands landlords
the wand grants `realty.command.rename` with it. The bypass uses `.others`, as
`subregion.delete`, `terminate`, `modify`, `add` and `remove` do. Both nodes are
new, so nothing is renamed for operators. Both are declared in
`paper-plugin.yml`.

The command is a new `RenameCommand` record implementing `CustomCommandBean`,
registered like `DeleteCommand`. `<region>` uses `WorldGuardRegionParser`.
`<new-name>` is a single string argument. The console may run the command; it
acts with `ActorContext.console()`.

## Flow

```
RenameCommand.execute                                main thread
  ├─ fire RegionRenameEvent (cancellable)
  └─ api.renameRegion(region, newName, actorContext)
        RealtyPaperApiImpl.renameRegion
          ├─ check the name, WorldGuard, capture       main thread
          ├─ non-bypass: check the subregion parent    main thread, then db thread
          ├─ realtyApi.renameRegion(...)               db thread
          └─ on Success:                               main thread
               swap the WorldGuard region and save
               move sign cache and capture cooldown entries
               redraw loaded signs
  fire RegionRenamedEvent, send the result message, notify the tenant
```

The command builds the actor on the database executor with
`ActorContexts.forRegion(player, bypass, region)`, as the subregion delete does.

`renameRegion` runs inside `serializeByRegion` for both the old and the new
name. It takes the two queues in alphabetical order of the names, so two
renames that cross (`a` to `b` while `b` to `a`) cannot wait on each other.

### Step 1: checks on the main thread

- The new name does not match the pattern: `InvalidName`.
- The lower-cased new name equals the region's id: `SameName`.
- The region is not a cuboid or a polygon: `UnsupportedRegion`. Realty only
  creates those two shapes.
- `RegionManager.hasRegion(newName)`: `NameTaken`.
- `CaptureRegistry` has a capture in flight for the region: `CaptureRunning`.
- Without bypass: the WorldGuard parent is missing: `NotAuthorized`.

### Step 2: the database

Without bypass, the paper layer first checks on the database executor that the
parent is a registered freehold (`getFreeholdContract(parentId, worldId)`), and
returns `NotAuthorized` if it is not. It then calls the backend.

### Step 3: the WorldGuard swap

A new class `WorldGuardRegionRename`, beside `WorldGuardRegionRemoval`, does
the swap on the main thread:

1. If `hasRegion(newName)` is now true, stop and report the collision.
2. Build a region with the new id and the old region's shape: a
   `ProtectedPolygonalRegion` from the old points and Y range, or a
   `ProtectedCuboidRegion` from the old corners. The transient setting is
   carried over.
3. `newRegion.copyFrom(old)`. This is WorldGuard's own method on
   `ProtectedRegion`, the one `/rg redefine` uses. It copies:
   - the owners and the members, each as a whole domain, so both the players
     and the permission groups in it;
   - the whole flag map, so built-in flags, custom flags registered by Realty
     or another plugin, and their region-group settings;
   - the priority and the parent.

   With the shape and the transient setting from step 2, that is every field
   a WorldGuard region has apart from its id.
4. `addRegion(new)`.
5. For every region in the manager whose parent is the old region, call
   `setParent(new)`.
6. `removeRegion(oldName)`.
7. `save()`.

On a collision in step 1, `renameRegion` calls the backend again with the names
exchanged and `ActorContext.console()`, so the database returns to the old
name, and returns `NameTaken`. If that call fails too, it logs at SEVERE with
both names and returns `Error`.

A failed `save()` does not undo the rename. The region manager already holds
the new region in memory and WorldGuard writes it on its next save. The result
is `WorldGuardSaveError`, which carries the renamed region so the caller still
fires the renamed event.

### Step 4: in-memory state

- `SignCache.renameRegion(oldName, newName, worldId)` (new method) moves the
  region's positions to the new key and replaces each entry with one that
  carries the new name. Signs in unloaded chunks need nothing: they are loaded
  from the database by region id.
- `CaptureCooldown.rename(oldName, newName, worldId)` (new method) moves the
  cooldown entry. Why: otherwise a rename would reset the capture cooldown.
- `signTextApplicator.updateLoadedSigns(world, newName, state, placeholders)`,
  with the state and placeholders from `getRegionWithState(newName, worldId)`.
  This is what redraws the name on signs.

The flag profile is not applied again. Why: `applyFlags` first clears every
flag on the region and then sets the profile's flags, so it would remove any
flag set by hand that the copy has just carried over. The flags therefore stay
exactly as they were. The cost is that a profile flag whose text includes the
region's name (a greeting, say) shows the old name until the region next
changes state or the configuration is reloaded, when the profile is applied as
usual. For the same reason a grouped profile that lists the new name starts to
apply then, and one that listed the old name stops applying then.

## Backend

New method on `RealtyBackend`:

```java
sealed interface RenameRegionResult {
    /** {@code tenantId} is the tenant of the region's lease, if it has one. */
    record Success(@Nullable UUID tenantId) implements RenameRegionResult {}
    record NoRegion() implements RenameRegionResult {}
    record NameTaken() implements RenameRegionResult {}
    record NotAuthorized() implements RenameRegionResult {}
}

@NotNull RenameRegionResult renameRegion(@NotNull String worldGuardRegionId,
                                         @NotNull String newWorldGuardRegionId,
                                         @NotNull UUID worldId,
                                         @NotNull ActorContext ctx);
```

It follows `deleteVacantLeasehold`. In one non-autocommit session:

1. Read the region by the old name. None: `NoRegion`.
2. Read the region by the new name. One exists: `NameTaken`.
3. Read the lease. Without bypass: no lease, or the actor does not manage its
   landlord: `NotAuthorized`. Take `requiredLandlordPartyId` from the lease's
   landlord; with bypass it is `NULL`.
4. Rename the region row with the landlord condition in the statement:

   ```sql
   UPDATE RealtyRegion rr
   SET rr.worldGuardRegionId = #{newWorldGuardRegionId}
   WHERE rr.worldGuardRegionId = #{worldGuardRegionId}
     AND rr.worldId = #{worldId}
     AND (#{requiredLandlordPartyId} IS NULL OR EXISTS (
         SELECT 1
         FROM Contract c
         INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = c.contractId
         WHERE c.realtyRegionId = rr.realtyRegionId
           AND c.contractType = 'leasehold'
           AND lc.landlordPartyId = #{requiredLandlordPartyId}))
   ```

   If it changes no row, roll back and read again: the region is gone
   (`NoRegion`) or the landlord changed (`NotAuthorized`). If it fails on the
   unique key `unique_worldGuardRegionId_worldId`, another transaction took
   the name: `NameTaken`.
5. Move the name-keyed rows:

   ```sql
   UPDATE FreeholdHistory SET worldGuardRegionId = #{new}
   WHERE worldGuardRegionId = #{old} AND worldId = #{worldId};
   -- the same statement for LeaseholdHistory and AgentHistory

   UPDATE IGNORE RegionTag SET worldGuardRegionId = #{new}
   WHERE worldGuardRegionId = #{old};
   DELETE FROM RegionTag WHERE worldGuardRegionId = #{old};
   ```

   The `IGNORE` and the `DELETE` cover a tag the new name already has: the
   primary key is `(tagId, worldGuardRegionId)`, so the duplicate row is
   dropped instead of failing the rename.
6. Commit.

New mapper methods: `RealtyRegionMapper.renameGuarded`, a `renameRegion` on each
of the three history mappers, and `RegionTagMapper.moveToRegion`. The
leftover tag rows are removed with the existing `deleteByRegionId`. Each has the neutral
declaration and the `Maria…` statement, as the other mappers do.

No migration. No table or column changes; the history rows are moved by the
rename itself.

No history row is written for the rename. The history tables record sales,
leases and agents, and their event types are enums that a new value would
require a migration for.

`SaleFee.regionName` and `PendingRefund.regionName` are not changed. They
record the name at the time of a sale.

## Paper API

New method on `RealtyPaperApi` (additive, a minor version):

```java
sealed interface RenameRegionResult {
    /**
     * @param region            the region under its new name
     * @param oldName           the name it had
     * @param tenantId          the tenant to notify, if the region is rented
     * @param leftGroupedProfile whether a grouped profile in the configuration listed the old name
     */
    record Renamed(@NotNull WorldGuardRegion region, @NotNull String oldName,
                   @Nullable UUID tenantId, boolean leftGroupedProfile) implements RenameRegionResult {}
    /** Renamed, but WorldGuard could not save; it retries on its next save. */
    record WorldGuardSaveError(@NotNull Renamed renamed, @NotNull String error)
            implements RenameRegionResult {}
    record InvalidName() implements RenameRegionResult {}
    record SameName() implements RenameRegionResult {}
    record NameTaken() implements RenameRegionResult {}
    record NotRegistered() implements RenameRegionResult {}
    record NotAuthorized() implements RenameRegionResult {}
    record UnsupportedRegion() implements RenameRegionResult {}
    record CaptureRunning() implements RenameRegionResult {}
    record Error(@NotNull String message) implements RenameRegionResult {}
}

@NotNull CompletableFuture<RenameRegionResult> renameRegion(
        @NotNull WorldGuardRegion region, @NotNull String newName, @NotNull ActorContext ctx);
```

`NoRegion` from the backend becomes `NotRegistered`.

`RealtyPaperApiImpl` gains three constructor parameters: `WorldGuardRegionRename`,
`CaptureRegistry` and `CaptureCooldown`. All are passed in from `Realty`, which
now holds one `CaptureCooldown` shared with the schematic command.

Two small additions to classes in `realty-paper-api`, both additive:

- `SignCache.renameRegion(String oldId, String newId, UUID worldId)`.
- `RegionProfileService.hasGroupedProfile(String regionName)`: whether a
  grouped flag or sign profile lists the name. It gives `leftGroupedProfile`.

### Events

Both extend `RealtyRegionEvent`, like the delete events.

- `RegionRenameEvent` (cancellable): the region under its current name,
  `getNewName()`, and `@Nullable UUID getActorId()`.
- `RegionRenamedEvent`: the region under its new name, `getOldName()`, and
  `@Nullable UUID getActorId()`.

The actor is `null` for the console. Why this differs from the delete events,
which do not fire for the console: a plugin that stores region names has to
hear about every rename, or its records point at a name that no longer exists.

The command fires both, as the other commands do. A plugin that calls
`renameRegion` directly fires nothing; this is how every other API method
behaves.

## Messages

New keys under `rename.*` in `messages.yml`, with constants in `MessageKeys`:

| Key | When |
|---|---|
| `success` | Renamed; `<old>`, `<region>` |
| `left-grouped-profile` | Added to the reply when the old name was listed in a grouped profile; `<old>` |
| `invalid-name` | The new name does not match the pattern; `<name>` |
| `same-name` | The new name is the current name |
| `name-taken` | A region with the new name exists; `<name>` |
| `not-registered` | The region is not registered with Realty; `<region>` |
| `not-authorized` | Not a subregion whose landlord the caller manages; `<region>` |
| `unsupported-region` | The region is neither a cuboid nor a polygon; `<region>` |
| `capture-running` | A schematic capture of the region is running; `<region>` |
| `worldguard-save-error` | Renamed, WorldGuard save failed; `<region>`, `<error>` |
| `error` | Anything else; `<error>` |

Under `notification.*`: `region-renamed` (to the tenant; `<old>`, `<region>`),
sent through `RealtyNotificationEvent` as the subregion delete does. It is not
sent when the tenant is the actor.

A cancelled `RegionRenameEvent` replies with `common.action-cancelled`.

## Conventions

- `commands`: the project registers commands with Incendo Cloud and
  `CustomCommandBean`, so the playbook's Brigadier default is set aside. One
  record, two declared nodes, a sealed result with one message per case.
- `database`: no migration. New statements on four mappers, each as a neutral
  method and a `Maria…` text block. The rename and the row moves share one
  transaction. Outcomes are a sealed result.
- `threading`: database work on `dbExec`, WorldGuard, signs and events on the
  main thread. The chain ends in `exceptionally`, which logs through the
  plugin logger and returns `Error`.
- `localisation`: every reply through `MessageContainer`.
- `testing`: see below.
- `release`: two new API methods, two new events and two additions to
  `realty-paper-api` classes. All additive; ships in the next minor version.

## Error handling

- WorldGuard save fails after the swap: `worldguard-save-error`. The rename
  stands.
- The new name appeared in WorldGuard between the check and the swap: the
  database rename is reversed and the reply is `name-taken`.
- The reversal fails: logged at SEVERE with both names, reply `error`. Realty
  then knows the region by the new name and WorldGuard by the old one. An
  admin repairs it with `/rg` by hand. This needs a second failure straight
  after a successful write, so no automatic repair is built.
- An unexpected exception: logged, reply `error`.

## Testing

Backend (`SubregionDeleteTest` style, Testcontainers MariaDB), in a new
`RegionRenameTest`:

- **Completeness.** Create a region and fill every feature for it: a
  contract, a history row of each kind, tags, a sign, a schematic, a fee rule,
  an offer and a bid. Rename it. Then read `information_schema.COLUMNS` for
  every column named `worldGuardRegionId` or `regionName` in the schema, and
  count the rows in each that still hold the old name. The count must be zero
  everywhere except an allow-list in the test: `SaleFee.regionName` and
  `PendingRefund.regionName`. Why: the tests below only cover the tables this
  design knows about. This one fails when a later migration adds a name-keyed
  table and the rename is not updated. It finds columns by name, so a table
  that stores the name under another column name is not caught; none does
  today.
- A manager of the landlord renames a leasehold: the `RealtyRegion` row has the
  new name, and the lease is read by the new name and not by the old one.
- Rows in `FreeholdHistory`, `LeaseholdHistory` and `AgentHistory` move to the
  new name. Rows of a same-named region in another world do not.
- Tags move. A tag the new name already has leaves one row, and no row keeps
  the old name.
- Signs, the schematic and a fee rule are still read for the region after the
  rename.
- `SaleFee.regionName` keeps the old name.
- A rented leasehold: `Success` carries the tenant.
- The new name is registered in the same world: `NameTaken`. The same name in
  another world: `Success`.
- A caller who does not manage the landlord: `NotAuthorized`, nothing changed.
- A freehold without bypass: `NotAuthorized`. With bypass: `Success`.
- The console: `Success`.
- An unknown region: `NoRegion`.
- The race: the landlord is changed between the read and the update (by
  calling the mapper directly): `NotAuthorized`, nothing changed.

Paper layer (unit tests, executors as `Runnable::run`, the backend faked):

- `WorldGuardRegionRename` against a real in-memory `RegionManager`: a cuboid
  and a polygon keep their shape, members, owners, flags, priority and parent;
  a child region points at the new region; the old id is gone; an existing
  region with the new id reports a collision and changes nothing.
- `SignCache.renameRegion`: positions are found under the new name, entries
  carry it, and nothing is left under the old name.
- `renameRegion`: each check in step 1 gives its result and makes no backend
  call; a collision at the swap calls the backend with the names exchanged.
- Every `rename.*` key exists in `messages.yml` (the existing message-key test
  pattern).

Live-server checks, listed in the plan: rename a wand-made subregion as its
landlord and see the sign redraw; rename a rented subregion and see the
tenant's notification; rename a freehold with subregions as an admin and check
`/rg info` shows the children under the new parent; try a taken name; check
`/realty history` and `/realty info` under the new name; check the web
explorer shows the region at the new address.

## Known limitations

- **Fee and refund records keep the old name.** `SaleFee.regionName` and
  `PendingRefund.regionName` are snapshots of a past sale.
- **Grouped profiles in the configuration.** A grouped flag or sign profile
  lists region names. After a rename the old name no longer matches, so the
  region falls back to the global profile until an operator edits the
  configuration. The reply warns about this. Realty does not rewrite the
  operator's file.
- **Flag text keeps the old name for a while.** The rename does not apply the
  flag profile again (see Step 4), so a profile flag that includes the region's
  name shows the old one until the next state change or reload.
- **Tags have no world column.** `RegionTag` is keyed by name only. If two
  worlds each have a region with the old name, the rename moves the tags of
  both. Tags already behave this way: the two regions share their tags today.
- **A reused name inherits history.** History rows are keyed by name and
  survive a delete. If the new name belonged to a region deleted earlier, its
  rows appear under the renamed region. The subregion delete has the same
  limit. For the same reason, a reversed rename (see Error handling) also
  moves any such rows to the old name.
- **Old links and old notifications.** Web explorer addresses contain the
  name, so links to the old name return 404. Notifications already delivered
  keep the old name.
- **Only the tenant is notified.** An admin rename does not notify the
  landlord or the title holder.
- **A capture can start during a rename.** The capture check runs before the
  database write. A capture started in that gap saves under the old name and
  fails; the player captures again.
- **Other WorldGuard region types.** A region that is neither a cuboid nor a
  polygon is refused. Realty never creates one.
- **Other plugins.** A plugin that stores region names must listen for
  `RegionRenamedEvent`. A rename made through the API without the command
  fires no event.
