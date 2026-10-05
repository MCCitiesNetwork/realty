# Set Command Authority — Design

Date: 2026-10-05
Status: Revised for the party model, pending review

Companion spec: `2026-09-29-treasury-parties-design.md`. That work made
landlords and authorities parties and introduced `ActorContext`. This design
builds on it and does not change it.

An earlier version of this design was written and built against a `main` that
predated the party model. The branch `feat/set-command-authority` holds that
work. It is not mergeable, and it is kept as a reference; see "What carries
over".

## Purpose

Make `/realty set`, `/realty unset` and `/realty modify` predictable. A player
should be able to tell, before running a command, whether they are allowed to
run it and when the change will take effect.

## The problem today

`set` applies several different authority rules, and `modify` overlaps with it.

| Command | Who passes the ownership check | Default |
|---|---|---|
| `set price`, `set duration`, `set maxextensions` | Freehold: the WorldGuard owner. Leasehold: a manager of the landlord, only while vacant, and only with an extra `.leasehold` node | op |
| `set tenant` | A manager of the landlord, only while vacant | op |
| `set landlord` | Someone who may reassign the landlord, only while vacant | op |
| `set titleholder`, all of `unset` | The WorldGuard owner | op |
| `set authority` | Anyone holding the node | op |
| `modify price`, `modify duration`, `modify maxextensions` | The tenant or a manager of the landlord, only while rented | everyone |

This causes six faults:

1. A landlord with a vacant lease has no working command. `modify` refuses and
   points at `set`, which is op-only and needs a `.leasehold` node.
2. The `.leasehold` nodes are checked in code but not declared in
   `paper-plugin.yml`.
3. `.others` means two things: "act on a region you do not control" and "skip
   the tenant's notice". An admin cannot have the first without the second.
4. `set landlord` and `set tenant` on a rented region tell the player to use
   `modify`, which cannot change either.
5. `unset tenant` checks the WorldGuard owner. On a rented region that is the
   tenant, so the tenant passes and the landlord is refused.
6. The WorldGuard owner list lags the database during a purchase. The database
   names the buyer first, and WorldGuard is updated a tick or so later. A
   seller who runs `set price` in that moment passes the check and puts the
   buyer's region up for sale.

## Design

Two rules replace the table above. Every `set` and `unset` command uses both,
except `set authority`, which stays as it is.

### Rule 1: who may act

The caller must act for the party that holds the region. "Acts for" is the
existing `ActorContext` test.

| Region | Command | The caller must |
|---|---|---|
| Leasehold | Terms, `set tenant`, `unset tenant` | Manage the landlord |
| Leasehold | `set landlord` | Be allowed to reassign the current landlord, and manage the new one. This is today's rule, unchanged |
| Freehold with a title holder | Any | Be the title holder |
| Freehold with no title holder | Any | Manage the authority |

- The command's `.others` node is the context's bypass. No flag is needed.
- The console bypasses every check, as it does today.
- The WorldGuard owner list is no longer consulted.
- A tenant does not hold the region. A tenant may only ask for new terms; see
  rule 2.
- `set authority` keeps the rule the party design gave it: the node alone
  decides, no holder check applies, and it takes no `--now`.

### Rule 2: when the change lands

Term commands are `set price`, `set duration` and `set maxextensions`.

| Region state | Caller | Outcome |
|---|---|---|
| Freehold, or vacant lease | Holds the region | Applies now |
| Rented | The tenant | A request a manager of the landlord must accept |
| Rented | Holds the region, not the tenant | Scheduled for the tenant's next renewal |
| Rented, `--now` | Holds the region, and has `realty.command.set.now` | Applies now |
| Rented, lease ending | Anyone, without `--now` | Refused, as `modify` refuses today |

Holder commands are `set landlord`, `set tenant`, `unset tenant`,
`set titleholder`, `unset titleholder` and `unset price`. They have no "next
renewal" form.

| Region state | Outcome |
|---|---|
| Freehold, or vacant lease | Applies now |
| Rented | Refused unless `--now` is given and the caller has `realty.command.set.now` |

Details:

- **The tenant is checked first**, as the backend already does for proposals
  and terminations. A caller who is the tenant makes a request, even if they
  also manage the landlord or hold `.others`. To override, they pass `--now`,
  which needs them to hold the region (or `.others`) and `set.now`.
- **`--now` on a region that is not rented** is accepted and has no effect.
- **`set maxextensions -1`** (unlimited) cannot be scheduled or requested. A
  lease stores "unlimited" as no limit at all, and a pending change reads a
  missing limit as "leave the limit alone". On a rented region the command
  refuses `-1` without `--now` and says why.
- **The console cannot schedule for a landlord that is not a player.** A
  scheduled change records a proposing player: the landlord when the landlord
  is a player, otherwise the acting player. The console is neither, so on a
  rented lease of an account or group it must pass `--now`. The refusal says
  so.

### Command surface

| Command | Change |
|---|---|
| `set price\|duration\|maxextensions <value> [region] [--now]` | Follows both rules |
| `set tenant\|titleholder <player> [region] [--now]` | Follows both rules |
| `set landlord <name> [region] [type flag] [--now]` | Follows both rules |
| `unset price\|titleholder\|tenant [region] [--now]` | Follows both rules |
| `set authority <name> [region] [type flag]` | Unchanged |
| `modify price\|duration\|maxextensions` | Removed. `set` covers them |
| `modify accept\|reject\|withdraw\|inbox\|outbox` | Unchanged |

`--now` works whether or not the region is named. The region arguments use the
existing `RegionOrFlagParser`, which lets a flag follow an omitted region, as
`set landlord` already does for its type flags.

### Permissions

| Node | Default | Change |
|---|---|---|
| `realty.command.set.price`, `.set.duration`, `.set.maxextensions`, `.unset.price` | true | Was op |
| `realty.command.set.landlord`, `.set.tenant`, `.set.titleholder`, `.set.authority`, `.unset.tenant`, `.unset.titleholder` | op | Unchanged |
| `realty.command.<set\|unset>.<sub>.others` | op | Now means only "act on a region you do not hold" |
| `realty.command.set.now` | op | New. One node for every `set` and `unset` command |
| `realty.command.set.<sub>.leasehold` | — | Removed from the code |
| `realty.command.modify.price`, `.modify.duration`, `.modify.maxextensions` | — | Removed |
| `realty.command.modify.others` | op | Unchanged. Covers `accept`, `reject` and `withdraw` |

### Messages

The reply always states which outcome happened.

- Applied now uses each command's existing success message. Scheduled, and
  sent to the landlord as a request, reuse the two existing `modify` proposal
  messages.
- New: "rented, add `--now` to change this immediately", "rented, and you may
  not change it while it is rented", "unlimited extensions cannot wait for a
  renewal", "the console must use `--now` for this landlord", "just rented,
  run it again" and "just vacated, run it again".
- Removed: `set.occupied-use-modify`, `set.leasehold-no-permission`,
  `modify.not-occupied`.
- The help text drops the `modify price|duration|maxextensions` line and shows
  `[--now]` on the `set` and `unset` lines.

## Guarding the write

The command reads the contract, decides, and then writes. The holder or the
tenant could change between the read and the write, so the backend decides
again as part of the write.

Every backend setter behind these commands takes the `ActorContext` and an
"only if vacant" input, the shape `setLandlord`, `setRentable` and
`proposeModification` already have. These are `setPrice`, `unsetPrice`,
`setDuration`, `setMaxRenewals`, `setTenant`, `setTitleHolder`, and
`setLandlord`, which gains only "only if vacant".

1. The backend reads the contract and applies rule 1 with the context. A
   caller who does not hold the region gets a `NotAuthorized` result.
   `setLandlord` keeps its two existing results for this.
2. The `UPDATE` then carries two conditions: the holder is still the one just
   checked (the landlord party id, or the title holder id), and, when "only if
   vacant" is on, the lease has no tenant. This is the pattern `rentRegion`,
   `unrentRegion` and `renewLeasehold` already use.
3. If the contract exists but the update changes no row, the backend reads
   committed state again and answers `NotAuthorized` (holder changed),
   `Occupied` (a tenant arrived), or `UpdateFailed`.

Notes:

- The command turns "only if vacant" on whenever `--now` does not apply.
- On `Occupied` the command changes nothing and tells the caller the region
  was just rented and to run the command again. The reverse case is the same:
  if the command chose to schedule and the tenant has just left,
  `proposeModification` answers `NotOccupied`, and the caller is told to run
  the command again.
- MariaDB 11.7 raises "Record has changed since last read" when an `UPDATE`
  follows a read of a row that has since changed. The setter therefore ends
  its read before the guarded `UPDATE`, and again before reading committed
  state. The earlier branch found and tested this.
- The existing signatures stay and mean "console context, not only if
  vacant", so other plugins that call them are unaffected.
- No lock and no schema change are needed.

## Signs

The term setters write only to the database today, so a region's loaded sign
keeps the old price until its chunk reloads, while `rent` and `buy` charge the
new one. That was tolerable while these commands were operator-only. After a
successful `setPrice`, `unsetPrice`, `setDuration` or `setMaxRenewals`, the
plugin redraws the region's loaded signs from the stored contract, as
`setTenant` does. Flags are left alone: no term change alters a region's
state, and re-applying flags would clear ones set by hand.

## Structure

One decision function chooses the outcome. It takes the region state (none,
freehold, vacant lease, rented lease, ending lease), whether the caller is the
tenant, whether the caller holds the region, whether the context bypasses,
whether the caller has `set.now`, whether `--now` was given, whether the
command is a term or a holder command, and whether unlimited extensions were
asked for. It returns one of: apply now, schedule, request, or a specific
refusal. It has no Bukkit or database dependency, so it is unit-tested as a
table.

One router class builds the context with the existing `ActorContexts`, reads
the contracts, calls the function, sends refusals, and hands the outcome and
the context to the command. It delivers on the main thread, because the
cancellable events (`PriceSetEvent`, `LeaseModifyProposeEvent`,
`TitleTransferEvent`) must fire there. An exception in a command's callback is
logged and reported to the sender.

`SetCommandGroup` and `UnsetCommandGroup` use the router. `authorizeLeaseholdSet`,
`authorizeAsLandlord`, `LandlordGate` and the inline WorldGuard-owner checks
are removed. Every write call reports a failed future to the sender and logs
it.

The schedule and request outcomes call the existing `proposeModification`. The
storage, merging and renewal-time application of a pending change do not
change, and the same events fire as today, so notification adapters keep
working.

`ModifyCommandGroup` loses its three proposal subcommands and `executePropose`.

`set price` fires `PriceSetEvent` before it checks authority today. It moves to
after the check, so a refused caller cannot trigger listeners. It also fires
before a scheduled change and before a tenant's request; its documentation
says so.

## What carries over

From `feat/set-command-authority`, as a reference to copy from and adapt:

- The decision function and its table test. Its inputs change from "role" to
  the flags listed above; the rules are the same.
- The message copy, the permission and help changes, and the removal of the
  `modify` term subcommands.
- The guarded-write tests that interleave two database sessions.
- The sign redraw and its tests.

Redone against the party model: the backend guards (they compared a player id;
they now check the context and compare the holder just read), the router's
lookup, and the command handlers.

## Not in scope

- `set authority`, `transfer`, `rent`, `unrent`, `terminate`, `rentable` and
  `extend`.
- `transfer` has the same WorldGuard-owner check and the same purchase-moment
  gap. It is op by default. Fixing it is a follow-up.
- A scheduled change outlives the tenancy it was scheduled for. Pending
  changes are stored per contract, and nothing resolves them when the tenant
  leaves, so a new tenant's first renewal can apply the previous tenancy's
  change. This predates this work and was equally reachable through `modify`.
  It is a follow-up.
- The REST service and the web explorer. Neither issues these commands.

## Upgrade notes for server operators

- Players gain `set price`, `set duration`, `set maxextensions` and
  `unset price` on regions they hold. Operators who want the old behaviour
  negate those nodes.
- Groups that held a `.others` node no longer change a rented region
  instantly. They also need `realty.command.set.now` and must pass `--now`.
- Servers that negated `realty.command.modify.price`, `.duration` or
  `.maxextensions` to stop tenants requesting terms must now negate
  `realty.command.set.price`, `.duration` or `.maxextensions`.
- Scheduling a change on someone else's rented lease now needs
  `realty.command.set.<sub>.others`. `realty.command.modify.others` covers
  only `accept`, `reject` and `withdraw`.
- Grants of the removed `modify` and `.leasehold` nodes can be deleted.
- New message keys are added to an existing `messages.yml` automatically. The
  `help.management` list is not: defaults only add missing keys. Operators
  replace that list with the one in `defaults/default-messages.yml` to drop
  the removed `modify` line and show `--now`.
- A WorldGuard owner added by hand, outside the plugin, no longer counts as
  holding a freehold. They need `.others`.

## Testing

- Unit: the decision function, one case per row of both tables, plus the
  tenant who also manages the landlord, the lease-ending case, unlimited
  extensions on a rented lease, and the console on an account's lease.
- Unit: the router delivers on the main thread, and reports a callback that
  throws.
- Unit: a real command-parser test that `--now` is read as the flag when the
  region is omitted, and that `set maxextensions -1` still parses.
- Backend, per guarded setter: a manager of the landlord succeeds; a stranger
  gets `NotAuthorized` and nothing changes; a rented lease with "only if
  vacant" answers `Occupied` and is left unchanged; setting the same value
  succeeds; the existing signature still applies unconditionally.
- Backend: a rent, a landlord change and a title change committed from a
  second session between the read and the write are answered `Occupied` or
  `NotAuthorized`.
- Backend: a former title holder cannot reprice a freehold; a manager of the
  authority can reprice an unsold one.
- Plugin API: a successful term change redraws signs; a refused one does not.
- In game: a landlord sets terms on a vacant lease; a landlord sets terms on a
  rented lease and the tenant sees them at renewal; a member of a business
  does the same for the business's lease; a tenant's `set price` reaches the
  landlord's inbox; an admin without `--now` schedules; an admin with `--now`
  applies, with and without naming the region; `unset tenant` by the landlord
  works and by the tenant is refused; a sign shows the new price at once.
