# Extension Cap Permissions — Design

Date: 2026-11-21
Status: Draft, awaiting review.

## Purpose

Any player who can create a subregion can give its lease unlimited extensions. The subregion
dialog offers an "Unlimited renewals" toggle that starts switched on
(`SubregionState.unlimitedRenewals = true`), and no permission or setting limits it. The same is
true of `/realty set maxextensions -1`, `/realty create leasehold` and `/realty register
leasehold`, which all accept `-1` (unlimited) or any count.

Servers want to decide per player, or per rank, how many extensions that player may grant.

## Design

### The permission nodes

```
realty.leasehold.extensions.unlimited   # may grant unlimited extensions
realty.leasehold.extensions.<n>         # may grant up to n extensions, n >= 0
```

A player's cap is worked out from their effective permissions:

1. Holding `realty.leasehold.extensions.unlimited` means no cap.
2. Otherwise, the highest `<n>` among the `realty.leasehold.extensions.<n>` nodes the player holds
   is the cap.
3. A player with none of these nodes has a cap of **0**: every lease they set up runs for one
   period and cannot be extended.

Nodes set to `false` (negated) do not count. A suffix that is not a non-negative whole number, or
does not fit in an `int`, is ignored. Only `.unlimited` is declared in `paper-plugin.yml`, with
`default: op`, so ops are not limited to 0. Numbered nodes cannot be declared in advance, and are
read from `Permissible.getEffectivePermissions()`. A wildcard such as `realty.*` or
`realty.leasehold.extensions.*` grants `.unlimited`.

**Why 0 with no node.** The user chose this so a server is capped as soon as it upgrades,
instead of being capped only for players who were given a node. The cost is that every landlord
who is not op can only grant 0 extensions until the server grants nodes (see Operator impact).

**Why permission nodes, not LuckPerms meta.** A meta value (`realty-max-extensions: 10`) would
tie Realty to LuckPerms. Numbered nodes work with any permissions plugin and are how other
plugins already express limits such as home counts.

### Who is capped

The cap applies to the player whose action sets a lease's extension limit. These actors are
never capped:

- the console;
- a player using a command's existing admin permission for that action:
  `realty.command.set.maxextensions.others` for `set maxextensions`, and
  `realty.command.modify.others` for `modify accept`.

`create leasehold`, `register leasehold` and the subregion dialog have no `.others` permission.
For them, the extension nodes alone decide. An admin who should be uncapped there is given
`.unlimited`, which ops have by default.

### The extension cap type

A new type in `realty-paper`, `command/util/ExtensionCap.java`:

```java
/** How many extensions a player may give a lease. */
public sealed interface ExtensionCap {
    record Unlimited() implements ExtensionCap {}
    record AtMost(int max) implements ExtensionCap {}

    /** Reads the cap from the sender's permissions. The console is {@link Unlimited}. */
    static @NotNull ExtensionCap of(@NotNull CommandSender sender);

    /** Whether {@code maxExtensions} (-1 meaning unlimited) is within the cap. */
    boolean allows(int maxExtensions);
}
```

It is a pure function of the sender's permissions, so commands and the dialog call it directly.
No injected service is needed.

### Where the cap is checked

| Path | Check |
| --- | --- |
| Subregion dialog (`SubregionDialog`) | When the dialog opens, read the cap. `Unlimited`: unchanged. `AtMost(n)`: the "Unlimited renewals" toggle is not shown, `unlimitedRenewals` starts `false`, the count field starts at `min(3, n)` and its label reads "Max renewals (up to n)". `validate` refuses a count above `n`. `submit` checks again before calling `quickCreateSubregion`, in case permissions changed while the dialog was open. |
| `/realty create leasehold` (`CreateCommand.executeLeasehold`) | Refuse before creating when the cap does not allow `maxextensions`. |
| `/realty register leasehold` (`RegisterCommand.executeLeasehold`) | The same, for a player sender. The console is never capped. |
| `/realty set maxextensions` (`SetCommandGroup.executeSetMaxExtensions`) | Without `.others`: refuse when the cap does not allow the value. This covers both `--now` and a landlord scheduling the change for the next renewal (`Outcome.Schedule`). A tenant's request (`Outcome.Request`) is not checked, because the tenant is asking and the landlord decides. |
| `/realty modify accept` (`ModifyCommandGroup.executeResolve`) | Without `realty.command.modify.others`: the accepting player's cap must allow the proposal's `newMaxExtensions`. A proposal that leaves extensions unchanged (`null`) is always allowed. |

`/realty set price` and `/realty set duration` do not change extensions and are not checked.
A proposal can never carry unlimited (`-1`), because unlimited already needs `--now`
(`set.unlimited-needs-now`).

### Accepting a proposal atomically

The proposal's count must be checked in the same transaction that accepts it. Otherwise a tenant
could withdraw and propose a larger count between the check and the accept. The backend gets an
overload:

```java
// RealtyBackend
/**
 * As {@link #acceptModification(String, UUID, ActorContext)}, but refuses with
 * {@link ResolveModificationResult.AboveExtensionCap} when the pending proposal sets more
 * extensions than {@code extensionCap}. A {@code null} cap means no cap.
 */
@NotNull ResolveModificationResult acceptModification(@NotNull String worldGuardRegionId,
                                                      @NotNull UUID worldId,
                                                      @NotNull ActorContext ctx,
                                                      @Nullable Integer extensionCap);

sealed interface ResolveModificationResult {
    // ...existing records...
    /** The proposal sets {@code proposed} extensions; the accepting player may grant at most {@code cap}. */
    record AboveExtensionCap(int cap, int proposed) implements ResolveModificationResult {}
}
```

The existing three-argument method becomes a `default` that passes `null`, so callers that do
not cap keep working. `RealtyPaperApi` gets the same overload.

**Why not put the cap on `ActorContext`.** The cap matters to one decision. Putting it on
`ActorContext` would mean every caller that builds a context has to work it out, for actions
that never use it.

### Messages

New keys in `messages.yml` and `MessageKeys`:

- `leasehold.extensions-above-cap`: "You may grant at most `<cap>` extensions." Used by
  create, register, set and the dialog's validation, with `<cap>` the player's limit.
- `leasehold.extensions-unlimited-not-allowed`: "You may not grant unlimited extensions; you may
  grant at most `<cap>`."
- `modify.extensions-above-cap`: "This proposal sets `<proposed>` extensions on region
  `<region>`, but you may grant at most `<cap>`."

The final wording follows the house style of the surrounding keys (`<prefix>`, colours).

## Conventions that apply

- **Commands and permissions** (`commands`): the new nodes, the declaration of `.unlimited`, and
  the help lines for `create leasehold`, `register leasehold` and `set maxextensions`.
- **Localisation** (`localisation`): the three new keys, covered by the shipped-messages tests.
- **Database** (`database`): only the accept query changes. There is no schema change and no
  migration.
- **Threading** (`threading`): the cap is read on the main thread, where the command or dialog
  runs, before any database work. The accept check runs inside the existing backend transaction.
- **Release** (`release`): a new API overload and a new `ResolveModificationResult` record. This
  is a minor version, following the precedent of the 2.1.0 role-change results. An outside
  plugin with an exhaustive `switch` on `ResolveModificationResult` needs a new case when it
  recompiles.

## Operator impact

On upgrade, every player without a node is capped at 0. `realty.command.set.maxextensions`
defaults to `true`, so ordinary landlords can still run the command, but only with `0`. Before
deploying, a server should grant `realty.leasehold.extensions.<n>` or `.unlimited` to the ranks
that set up leases. The release notes must say this first.

## Error handling

- A refused value never reaches the backend, so nothing is written and nothing is recorded in
  history.
- The dialog reopens with the error shown at the top, as it does for other validation errors.
- `AboveExtensionCap` from accept leaves the proposal pending, so the landlord can reject it or
  the tenant can withdraw it.

## Testing

- `ExtensionCapTest`, using a fake `Permissible` with a set of attachments:
  - no nodes gives `AtMost(0)`;
  - `.unlimited` gives `Unlimited`, also when numbered nodes are present;
  - the highest numbered node wins;
  - a negated node is ignored;
  - malformed and out-of-range suffixes are ignored;
  - the console is `Unlimited`;
  - `allows(-1)` is true only for `Unlimited`.
- Backend test (Testcontainers, beside the existing modification tests): accept with a cap below
  the proposal returns `AboveExtensionCap` and leaves the proposal pending; a cap at or above it,
  a `null` cap, and a proposal with `null` extensions all succeed.
- Message tests: the new keys exist and their placeholders resolve.
- Live server, by hand, with two players (one with no node, one with `.extensions.5`):
  - the dialog hides the toggle and refuses 6;
  - `set maxextensions -1 --now` and `set maxextensions 6` are refused, and `5` succeeds;
  - a tenant proposes 8 and the capped landlord's accept is refused;
  - an op is unaffected throughout.

## Known limitations

- **Existing leases keep their limits.** A lease set up before the upgrade, or before a player's
  cap was lowered, keeps its extension limit, including unlimited. Lowering it would rewrite
  terms tenants already agreed to.
- **The cap belongs to the acting player, not the landlord party.** For a group or account
  landlord, each manager is capped by their own nodes, so two managers can grant different
  limits on the same land.
- **Permissions are read only while the player is online.** That is enough today, because every
  capped path has an online player or the console acting. A future offline path (for example a
  web API) would need its own way to work out a cap.
- **Tenant requests are not capped when made.** The landlord's cap is applied at accept, so a
  tenant can still send a request the landlord is unable to accept.
- **The area importer is not capped.** AreaShop imports keep their original limits, as an
  operator action.
