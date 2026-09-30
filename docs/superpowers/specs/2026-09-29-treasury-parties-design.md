# Treasury Parties — Design

Date: 2026-09-29
Revised: 2026-09-30, to describe stage 1 as built, and again the same day for
per-kind party tables. The decisions the stage 1 plan
took where this spec was silent are folded in, and so are the choices made while
building and reviewing it.
Status: Draft, pending review

Companion specs: `2026-09-02-realty-rest-api-design.md` (the public API this
feature extends in place), `2026-09-02-realty-query-service-design.md` (the
in-process module `realty-rest` already uses for player names; it gains account
names).

## Purpose

Today every party to a contract — landlord, freehold authority, titleholder,
tenant — is a player UUID. A government that owns and rents out land has to be
faked with a UUID that "owns" a Treasury GOVERNMENT account, and money reaches
that account only through a hidden rule in `TreasuryEconomyProvider` that
prefers a UUID's GOVERNMENT account over its PERSONAL one. Land with no owner
defaults to the all-zeros UUID, for which Treasury silently creates a phantom
PERSONAL account that accumulates income.

This feature makes a party a first-class value that can be a player, a Treasury
account (BUSINESS, GOVERNMENT or SYSTEM), or a permission group backed by a
Treasury account. Money moves only through the account the party names, the
right players can act for a non-player party, and no player UUID is needed
anywhere in that chain.

## Scope

The work is split into three stages, each with its own implementation plan.
This spec designs the whole model and fully specifies **stage 1**.

| Stage | Roles that accept non-player parties |
|---|---|
| 1 (this spec) | Leasehold landlord, freehold authority |
| 2 (later spec) | Freehold titleholder |
| 3 (later spec) | Leasehold tenant |

Stage 1 exposes all four roles as `Party` in the public API so that the API
breaks once. Titleholder and tenant remain `Personal` in data and commands until
their stages.

Out of scope: Vault banks, Towny/Factions economies, and any party kind not
backed by Treasury. Vault-only servers keep today's behaviour: players only.

## Party kinds

| Kind | Identified by | Money | Who acts for it |
|---|---|---|---|
| `PERSONAL` | player UUID | the player's PERSONAL account (Treasury) or balance (Vault) | the player |
| `BUSINESS`, `GOVERNMENT`, `SYSTEM` | Treasury account id | that account | per `account-managers` |
| `GROUP` | permission group name | the Treasury account mapped to the group | members of the group |

A PERSONAL party is referenced by UUID, never by account id: WorldGuard
ownership, "is this player the tenant", and `/realty list <player>` all work on
UUIDs, and Treasury resolves a UUID's PERSONAL account with
`resolveOrCreatePersonal`. A PERSONAL Treasury account can never be named as an
account party; the player form is the only way to express it.

A permission group can be added to a plot as a WorldGuard *member* without any
mapping (the existing `/realty add`). It becomes a *party* — able to hold any
role — only once mapped to an account, because every role raises or takes
money.

## Data model

### Java

In `realty-backend-api`, shared by the backend, the paper plugin and REST:

```java
public sealed interface Party {
    record Personal(UUID playerUuid) implements Party {}
    record Account(int accountId, AccountKind kind) implements Party {}
    record Group(String groupName, int accountId, AccountKind accountKind) implements Party {}
}

public enum AccountKind { BUSINESS, GOVERNMENT, SYSTEM }
```

The backend stores and compares parties and never calls Treasury.

Group names are stored in lower case and matched without regard to case, so
`Police --group` finds the group mapped as `police`.

### Schema (migration V19)

A party is one row in a base table that gives it an id and a kind, plus one row
in the table of that kind holding its details. Contracts and history point at
the base row.

```sql
CREATE TABLE Party (
    partyId INT PRIMARY KEY AUTO_INCREMENT,
    kind    ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL,
    UNIQUE (partyId, kind)
);

CREATE TABLE PersonalParty (
    partyId    INT PRIMARY KEY,
    kind       ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'PERSONAL' CHECK (kind = 'PERSONAL'),
    playerUuid UUID NOT NULL UNIQUE,
    FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind)
);

CREATE TABLE AccountParty (
    partyId     INT PRIMARY KEY,
    kind        ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'ACCOUNT' CHECK (kind = 'ACCOUNT'),
    accountId   INT NOT NULL UNIQUE,
    accountKind ENUM ('BUSINESS','GOVERNMENT','SYSTEM') NOT NULL,
    FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind)
);

CREATE TABLE GroupParty (
    partyId        INT PRIMARY KEY,
    kind           ENUM ('PERSONAL','ACCOUNT','GROUP') NOT NULL DEFAULT 'GROUP' CHECK (kind = 'GROUP'),
    groupName      VARCHAR(64) NOT NULL UNIQUE,
    accountPartyId INT NOT NULL,
    FOREIGN KEY (partyId, kind) REFERENCES Party (partyId, kind),
    FOREIGN KEY (accountPartyId) REFERENCES AccountParty (partyId)
);
```

- `Party.kind` says which table holds the details; `accountKind` says what
  kind of Treasury account. A new Treasury account type extends `accountKind`
  only.
- The composite `(partyId, kind)` foreign key means a kind row can only attach
  to a base row of its own kind, so no column is nullable and no multi-branch
  check is needed. Each child's `kind` column must be the same enum as
  `Party.kind`, because InnoDB compares enum foreign keys by position; the
  one-value `CHECK` pins it.
- What the database cannot require is that every base row has a kind row.
  `PartyMapper` is the only writer in the code and inserts both rows in one
  transaction; the manual fix in the release notes does the same. A base row
  with no kind row fails loudly when read.
- A group points at its account's party row, so an account's kind is stored
  once. `accountPartyId` is not unique: several groups may share one account.
  Mapping a group creates the account's party row if it has none. Nothing
  deletes an account party in stage 1; the foreign key would refuse it while a
  group points at it.
- An account's own kind is stored because Treasury does not allow it to change
  (`updateAccount` mutates only name, authorization, archive state, overdraft
  and credit limit) and because `realty-rest` cannot ask Treasury. Display
  names are **not** stored; accounts can be renamed, so names are looked up
  when shown.

Stage 1 replaces two role columns with party foreign keys:

| Table | Old column | New column |
|---|---|---|
| `LeaseholdContract` | `landlordId UUID` | `landlordPartyId INT` |
| `LeaseholdHistory` | `landlordId UUID` | `landlordPartyId INT` |
| `FreeholdContract` | `authorityId UUID` | `authorityPartyId INT` |
| `FreeholdHistory` | `authorityId UUID` | `authorityPartyId INT` |

The migration, in SQL only, drops nothing before it has been copied forward:

1. Create the four tables.
2. Collect the distinct UUIDs of the four old columns into a temporary table
   with an auto-increment id; insert those ids into `Party` as `PERSONAL` and
   the pairs into `PersonalParty`; drop the temporary table. `AUTO_INCREMENT`
   continues after the highest id used.
3. Add each new column nullable, fill it with `UPDATE … JOIN PersonalParty ON
   playerUuid = <old column>`, then make it `NOT NULL` with a foreign key to
   `Party(partyId)`.
4. Drop `idx_leasehold_contract_landlord`, `idx_freehold_contract_authority`
   and the old columns (`IF EXISTS`, so a database missing an index still
   migrates); index the new columns.
5. Make `LeaseholdHistory.tenantId` and `FreeholdHistory.buyerId` nullable.

Existing data becomes PERSONAL parties with no change in meaning.

A history entry with no tenant or no buyer stores `NULL` there. Before V19 such
an entry recorded the landlord as tenant, or the authority as buyer; old rows
are not changed.

### Entities and mappers

`LeaseholdContractEntity.landlordId()` becomes `landlord(): Party`;
`FreeholdContractEntity.authorityId()` becomes `authority(): Party`. Tenant and
titleholder keep their `UUID` components until their stages; the entities gain
`tenant()` and `titleHolder()`, which return a `Party.Personal`. Every read
left-joins the three kind tables on the party id (and `AccountParty` a second
time for a group's account), never the base table, and one shared MyBatis
result mapping switches on the kind found to build the `Party` value, so no
mapper assembles it by hand. The one exception is the locking (`FOR UPDATE`)
read of a freehold, which loads its authority with a second query so that the
lock does not reach party rows other contracts share.

A `PartyMapper` offers find-or-insert by identity, used by every write that
assigns a party:

- An account is identified by its `accountId` alone. A stored account whose
  kind differs from the one asked for is refused, never treated as the same
  party.
- A party is created as its base row and its kind row in one short
  transaction before the caller's, so that two commands naming the same new
  party at the same moment both succeed (the loser of the race re-reads the
  row the winner made). Such a party can outlive a write that is then rolled
  back; an unused party is harmless.
- A group is created by `/realty group map` only: the mapper finds or creates
  the account's party row, then inserts the group's base and kind rows.
  Changing the mapping repoints `accountPartyId`; unmapping deletes the group's
  two rows and leaves the account party.
- History writes only look up an existing row; they never create one.

The insert for a player who becomes a party, the common case:

```sql
START TRANSACTION;
SELECT partyId FROM PersonalParty WHERE playerUuid = ?;   -- found: use it, done
INSERT INTO Party (kind) VALUES ('PERSONAL');             -- the id comes back as a generated key
INSERT INTO PersonalParty (partyId, playerUuid) VALUES (?, ?);
COMMIT;
```

An account is the same with `AccountParty (partyId, accountId, accountKind)`.
Mapping a group inserts the account's two rows first if the account has none,
then `Party ('GROUP')` and `GroupParty (partyId, groupName, accountPartyId)`.
On each kind-row insert the database checks that `(partyId, kind)` exists in
`Party` with that kind, that a group's `accountPartyId` is an `AccountParty`
row, and that the natural key is unique. Contract and history inserts are
unchanged: they store the party id.

## Payments

`EconomyProvider` works on parties:

```java
double getBalance(@NotNull Party party);
@NotNull PaymentResult transfer(@NotNull Party from, @NotNull Party to, double amount,
                                @NotNull String ledgerMessage, @Nullable UUID initiator);
```

`initiator` is the player whose action caused the payment (for example, the
tenant whose unrent triggers a refund out of a government account), so
Treasury's ledger shows who caused each transfer. It is `null` for scheduled
events such as termination refunds taking effect. Treasury cannot store an empty
initiator, so `TreasuryEconomyProvider` sends the fixed id
`UUID.nameUUIDFromBytes("realty:system")` in its place; no account is created
for it. A scheduled termination refund that fails is logged with the tenant, the
amount and the landlord, so that it can be corrected by hand.

A new `PartyWallets` class in the paper plugin resolves the account a party
pays from and into:

| Party | Treasury | Vault |
|---|---|---|
| `Personal` | `resolveOrCreatePersonal(uuid)` | the player's balance |
| `Account` | `getAccountById(accountId)` | refused: requires Treasury |
| `Group` | `getAccountById(<the mapped account's id>)` | refused: requires Treasury |

- The GOVERNMENT > PERSONAL > BUSINESS preference in `TreasuryEconomyProvider`
  is removed. A `Personal` party always uses the PERSONAL account.
- A balance check reads the same account the transfer will use.
- An account that Treasury has archived or deleted since assignment, whose
  type no longer matches the stored kind, or that has started to require
  authorization, makes the payment fail with a clear message. These are the
  only unavoidable runtime failures, and they are outside Realty's control.
- On a Vault server, the balance of a non-player party reads as 0 and a
  transfer to or from one is refused.
- Every payment flow stays all-or-nothing, as established by the failed-purchase
  and failed-tenancy work: a payment that fails leaves nothing behind.
- The 2-decimal normalisation stays.

### Accounts requiring authorization

Treasury accounts can have `requiresAuthorization`, and `TransferRequest` has an
`authorizer` field that Realty always sends as `null`. Treasury rejects a
transfer out of such an account with no authorizer, so such accounts **cannot be
assigned as a party** (landlord, authority, or a group's account), and
assignment is refused with a dedicated message. Otherwise a tenant-triggered
refund out of that account could fail, and the unrent would roll back. Because
the flag can be turned on after assignment, a payment that meets such an account
also fails with a clear message (see above).

## Who acts for a party

The paper plugin builds one `ActorContext` per command, on the database
executor because the lookups do I/O, before calling the backend:

```java
record ActorContext(@Nullable UUID player,   // null for the console
                    Set<Party> manages,
                    Set<Party> reassigns,
                    boolean bypass)
```

`mayManage(party)` and `mayReassign(party)` test membership or the bypass.
Treasury cannot list the accounts a player authorizes, so the context is built
from candidate parties rather than from everything the player belongs to: the
parties on the region in question, plus any party being assigned. The inbox
uses every non-player party in the `Party` table. Other plugins build a context
through `RealtyPaperApi.actorContext(...)`.

| Party | In `manages` when | In `reassigns` when |
|---|---|---|
| `Personal(uuid)` | it is the player | it is the player |
| `Account` | `account-managers: members` → member or authorizer; `authorizers` → authorizer only | authorizer of the account |
| `Group` | the player is in the group (Vault permission API) | authorizer of the group's mapped account |

- `account-managers` is a server-wide setting in `settings.yml`, values
  `members` (default) or `authorizers`.
- Reassignment follows the money: moving a role away from a group is decided by
  whoever controls the group's account.
- `bypass` is the existing `*.others` admin permission for the command.
- Group parties require Vault's permission service, including on Treasury
  servers. Without Treasury, no group is managed.

The backend's identity checks become set membership:
`actorId.equals(lease.landlordId())` becomes `ctx.manages().contains(lease.landlord())`.
Sites changed in stage 1:

- Landlord: `RealtyBackendImpl` (landlord checks around lines 818, 1058, 1119,
  1461), `SetCommandGroup` (line 99), `SignCommand` (line 104),
  `RealtyPaperApiImpl.computeTerminationPlan` (line 525), and
  `cancelTermination` and `withdrawModification`: any manager of the landlord
  may withdraw a landlord proposal or cancel a landlord termination.
- Authority (offers and auctions): `RealtyBackendImpl` around lines 222, 1575,
  1609, 1690, 1725.

When deriving the role in termination, the tenant check stays first, as today.

A landlord proposal stores a `proposerId` (`UUID NOT NULL`): the landlord's
UUID when the landlord is a player, as today, otherwise the acting player's.

### Assigning a party

`/realty set landlord` requires the **current** landlord to be in the actor's
`reassigns`. For non-admins, it also requires the **new** landlord to be in
`manages`: nobody can hand a role to an account or group they do not belong to.
Admins with the `*.others` permission bypass both. As for every `/realty set` on
a leasehold, a non-admin can only change a vacant lease; an occupied one goes
through `/realty modify`. Only the landlord change asks for `reassigns` rather
than `manages` of the current landlord, so an authorizer of a group's account
can move a lease away from the group without being in it.

`/realty set authority` is decided by its permission `realty.command.set.authority`
alone, which defaults to `op`: whoever holds it may set the authority of any plot
to any party. The assignment rule above does not apply to it, and the
WorldGuard owner check and `realty.command.set.authority.others` are removed.

### Conflict of interest

"The authority cannot buy, bid on or make offers on its own land, and cannot be
invited as an agent" (`RealtyBackendImpl` around lines 648, 281, 1655, 114)
becomes: refused when the authority is in the actor's `manages`.

- Permission `realty.bypass.conflict-of-interest` lifts this membership-based
  rule.
- A party dealing with *itself* (Steve buying from Steve, GovSecurity paying
  GovSecurity) stays refused even with the permission.
- Vault cannot tell the groups of an offline player, so inviting an offline
  agent checks what it can, and accepting the invite checks again.

### Existing check fixed

`AddCommand` and `RemoveCommand` authorize with `getOwners().contains(uuid)`,
which ignores WorldGuard group owners. They switch to WorldGuard's
group-aware check so that group owners can use them.

The same UUID-only owner check appears in thirteen other places (`SetCommandGroup`,
`UnsetCommandGroup`, `TransferCommand`, `UnrentCommand`, `SchematicCommandGroup`
and the agent commands). In stage 1 no party is a WorldGuard owner group, so
they are left alone; stages 2 and 3 replace them with `ActorContext` checks
against the titleholder or tenant party (see *Later stages*).

## Commands

### Writing a party

A plain name is a player. One of `--government`, `--business`, `--system` or
`--group` changes its meaning; the flags are mutually exclusive. The flag is
written last, after the region if one is given: the command framework (Cloud)
reads flags only after the last argument, and a flag before the region is
refused.

```
/realty set landlord Steve                       → Steve (PERSONAL)
/realty set landlord GovSecurity --government    → GOVERNMENT account "GovSecurity"
/realty set landlord Acme --business             → BUSINESS account "Acme"
/realty set landlord Mint --system               → SYSTEM account "Mint"
/realty set landlord police --group              → group "police" (must be mapped)
```

- The name is parsed as text and resolved in the handler once flags are known.
- `--government` resolves by name (`getGovernmentAccountByName`). Treasury has
  no global name lookup for other kinds, so `--business` and `--system` resolve
  among the sender's own accounts (`getAccountsByMember`), with `#<id>`
  accepted for any account type.
- The account must exist, must not be archived, must match the flag's type, and
  cannot be PERSONAL.
- Tab completion offers online players first, then the sender's accounts and
  mapped groups.
- Type flags on a Vault server are refused: "requires Treasury".

### Stage 1 command changes

| Command | Change |
|---|---|
| `/realty set landlord <name> [region] [flag]` | any party kind; assignment rules above |
| `/realty set authority <name> [region] [flag]` | any party kind; permission only |
| `/realty create\|register leasehold --landlord <name>` | type flags apply to the landlord |
| `/realty create\|register freehold --authority <name>` | type flags apply to the authority; `--titleholder` stays player-only |
| `/realty set titleholder`, `set tenant`, `transfer` | unchanged until stages 2 and 3 |
| `/realty add\|remove <name> [--group]` | `g:` prefix removed; `--group` adds/removes a WorldGuard member group, no mapping needed |
| `/realty group map <group> <account> --government\|--business\|--system` | creates the group's party row or changes its account |
| `/realty group unmap <group>` | refused while any contract or history entry uses the group, because history references the party row |
| `/realty group list` | each group, its account, and how many contracts use it |
| `/realty list [owned\|authority\|landlord\|rented] [<name>] [--page <n>] [flag]` | shows a player's, account's or group's portfolio (see *Listings and statistics*); replaces the `--player` flag. `/realty me` stays |

In stage 2, when `create freehold` accepts type flags on both `--authority` and
`--titleholder`, a type flag is allowed only when exactly one of the two is
given.

A `GROUP` party row exists only once `/realty group map` has created it, so the
database guarantees every group party has an account. Changing a mapping
redirects future money without touching contracts.

### Display

One `PartyNames` helper replaces the `resolveName(uuid)` calls across info,
history, signs and placeholders: `Steve`, `GovSecurity (government)`,
`Acme (business)`, `Mint (system)`, `police (group)`. `/realty info` shows
member groups as `police (group)` instead of `g:police`. An account that is
missing or has no display name shows by its id, `#42 (government)`. The group
commands use the same helper. A history entry with no tenant shows `N/A`.

Account names are chosen by their owners, so a party's name is always inserted
into a message or sign as plain text: it is never read as MiniMessage
formatting, and never read a second time as a placeholder. Sign placeholders
are filled as before, including keys that are not valid MiniMessage tag names
such as `<Price>`, with one exception: a placeholder inside the quoted argument
of a tag other than `hover`, such as the command of
`<click:run_command:'/x <region>'>`, is left as written, so that a value can
never become part of a command.

### Messages and permissions

New message keys: unknown account; archived account; account type does not
match flag; type flags require Treasury; group not mapped; not allowed to
assign or reassign this party; account requires authorization (if the Treasury
check makes that rule necessary); more than one type flag; group still in use
(unmap); more than one of the sender's accounts matches the name; a type flag
given without a name; `--group` given where an account flag is required.

New permissions: `realty.command.group`, `realty.bypass.conflict-of-interest`.

### Unchanged in stage 1

- Subregion landlords still follow the parent titleholder, who is a player
  until stage 2; `/realty set landlord` can change a child's landlord afterwards.
- The AreaShop importer assigns players; its fallbacks read the new config
  defaults, and it skips a region whose fallback default did not resolve.
- Property tax: titleholders are still players.

## Configuration

`settings.yml`:

```yaml
account-managers: members        # members | authorizers

default-freehold-authority: { name: GovSecurity, type: government }
default-leasehold-landlord: { name: GovSecurity, type: government }
# type omitted → name is a player; `uuid: <uuid>` may be given instead of `name` for a player
# a business or system account is named as #<id>
default-freehold-titleholder: { name: Steve }   # optional; players only in stage 1
```

The `default-*-uuid` keys and the all-zeros default are removed; startup warns
about any left in the file. Every default must resolve at startup; if one does
not, startup logs an error and the commands that depend on it refuse to run
rather than fall back. This includes the titleholder default: a
`create`/`register freehold` that would use an unresolved one refuses, and never
creates a freehold with no titleholder. Defaults are resolved off the main
thread, so for a moment after startup those commands refuse as if a default were
missing.

Group mappings live in the `Party` table, not in configuration.

## Notifications

`RealtyNotificationEvent` keeps taking player UUIDs, so the chat, essentials
and player-notifications adapters do not change. A `PartyRecipients` helper,
called from `RegionNotificationListener` off the main thread, expands a party
into recipients:

- `Personal`: the player.
- `Account`: its managers per `account-managers`, from Treasury's member and
  authorizer lists, including offline members (the player-notifications adapter
  queues for them).
- `Group`: online members only; Vault cannot list offline group members.

## Listings and statistics

- `listModificationsAwaitingLandlord` takes the actor's `manages` set, so a
  proposal on a GovSecurity lease reaches every manager's inbox.
- `/realty list me` stays personal; portfolios of accounts and groups use
  `/realty list <name> --<type>`.
- A listing of a party's land has four parts, paged by one offset in this order
  and in a fixed order within each part: `owned` (the party holds the title),
  `authority` (it is freehold authority), `landlord` (it lets the leasehold,
  with or without a tenant) and `rented` (it is tenant). Only a player holds a
  title or rents in stage 1, so for an account or group only `authority` and
  `landlord` can be non-empty. Before 2.0.0 the part called `landlord` held the
  authority freeholds.
- `countByLandlord`, the owners leaderboard and statistics count per party and
  show the kind beside the name.
- The Plan extension's metrics are per player by design and count `Personal`
  parties only.

## REST

Extended in place, within `/v1`:

- `PlayerRef {id, name}` becomes `PartyRef {kind, id, name}` for every party
  field (`landlord`, `authority`, `tenant`, `titleholder`, buyer, and so on).
  `kind` is `personal`, `business`, `government`, `system` or `group`; `id` is a
  UUID when `kind` is `personal`, the account id for account kinds, and the group
  name for `group`. Player data is unchanged apart from `"kind": "personal"`.
- New endpoint `GET /v1/parties/{kind}/{id}/regions`, with the response shape of
  `/v1/players/regions` (its field is still called `player`, holding a
  `PartyRef`).
  - A group that is not mapped, or an account stored under a kind other than
    the one asked for, is `404 PARTY_NOT_FOUND`.
  - An account Realty does not store is listed empty under the kind asked for,
    with a `null` name: the route does not ask the module to name an account no
    contract names.
  - A personal `id` must be a full 36-character UUID and an account `id` a
    positive integer.
- Account names are resolved through the query-service module, which runs in the
  game server and can call Treasury, exactly as it already resolves player
  names. Group names come from `Party`.
- The OpenAPI document renames the type to `PartyRef` and states the `id` rule.
  A history entry leaves out `buyer` or `tenant` when it had none.
- The explorer shows a non-player party as plain text with its kind (for example
  `GovSecurity (government)`, or `#42 (government)` with no name); it has no
  page for one.
- `realty-rest`'s expected schema version is bumped to V19, and it refuses any
  other; it is started only after the plugin has migrated (see *Upgrade*).

Two changes are breaking for API consumers, and the release notes call both out.
They are taken in `/v1`, as `/v1/players/regions` took its `player=` change,
rather than opening `/v2`:

- The meaning of `id` widens; only data created after this ships can carry a
  non-UUID `id`.
- In a regions listing, `landlord` now holds the leaseholds the party lets
  instead of the freeholds it is authority of, which move to a new `authority`
  list; `category` gains `authority`.

Otherwise the JSON shape only gains fields.

## Public API

- Events and `RealtyPaperApi` expose `Party getLandlord()`, `getAuthority()`,
  `getTenant()` and `getTitleHolder()`, and the methods that assign a party take
  `Party`. Result records carry `Party` in place of the UUID. A method that
  assigns a titleholder or tenant fails its future for a non-player party until
  stages 2 and 3.
- `getTenant()` is `null` when the lease has no tenant, for example on a
  resolved proposal or a cancelled termination of a vacant lease; it no longer
  stands in the landlord.
- `RealtyPaperApi.actorContext(...)` builds an `ActorContext` for another
  plugin (see *Who acts for a party*).
- The old `get…Id()` methods and UUID forms are deprecated: they return the UUID
  for a `Personal` party and `null` otherwise. They are removed in 3.0.0.
- `realty-paper-api` and `realty-backend-api` take one major version bump in
  stage 1.

## Upgrade

The runbook in `docs/release-notes/2.0.0.md` is the operator's reference; in
outline:

1. Stop `realty-rest`, then the game server, and back up the database with both
   stopped.
2. Replace the plugin and module jars, and start the game server with the
   whitelist on. V19 converts all existing parties to PERSONAL.
3. Only once V19 has run, replace and start `realty-rest`; it refuses any other
   schema, so it cannot be deployed alongside the plugin.
4. For each legacy government UUID, run the manual fix: create the account's
   party (a `Party ('ACCOUNT')` row and an `AccountParty` row with
   `accountKind = 'GOVERNMENT'`) if there is none, then repoint that UUID's party id in `LeaseholdContract`, `LeaseholdHistory`,
   `FreeholdContract` and `FreeholdHistory` to it. The script moves nothing if
   either value is missing, and the notes say how to undo a wrong account id and
   how to repair an account stored under the wrong kind.
5. `/realty group map` any groups that should become parties.
6. Replace the `default-*-uuid` keys with the new party settings.
7. Lift the whitelist.

The whitelist keeps player-triggered payments from reaching a legacy UUID's
PERSONAL account between steps 2 and 4. A scheduled termination refund can
still fire in that window; it would be paid out of the legacy PERSONAL account
and can be corrected with a manual Treasury transfer.

The release notes also list the breaking changes: the `g:` prefix and the
`--player` flag are removed; `realty.command.set.authority.others` is removed;
the API major bump; and the two REST changes under *REST*.

### Treasury facts this design relies on

- A UUID owns at most one GOVERNMENT account (the manual fix maps each legacy
  UUID to exactly one account). Stated by the operator; not visible in the API.
- An account's type cannot change after creation (`updateAccount` does not
  mutate it).
- A transfer out of a `requiresAuthorization` account without an authorizer is
  rejected (see *Payments*).

## Testing

- **Migration:** V19 against seeded data in the `MariaSchemaMigratorTest` setup:
  every old UUID becomes one PERSONAL party with its base and kind rows,
  repointed rows match their originals, a kind row cannot attach to a base row
  of another kind, a group cannot point at a party that is not an account, and
  a child's `kind` cannot be anything but its own.
- **Mappers:** the shared result mapping builds each party kind; `PartyMapper`
  find-or-insert is idempotent and leaves exactly one base row and one kind
  row; mapping a group creates the account party when missing and reuses it
  when present.
- **Backend authorization:** `manages` and `reassigns` membership, admin bypass,
  conflict of interest with and without `realty.bypass.conflict-of-interest`,
  and self-dealing still refused under the permission.
- **Payments:** `PartyWallets` for every kind; archived account; kind mismatch;
  Vault refusing non-personal parties; initiator passed to Treasury. The
  `FailedPurchaseTest` and `FailedTenancyPaymentTest` scenarios gain account
  payees so that failed payments still leave nothing behind.
- **Commands:** exclusive type flags, unknown or archived accounts, PERSONAL
  account refused as an account party, unmapped group refused at assignment,
  `unmap` refused while in use, assignment rules for non-admins.
- **Notifications:** recipient expansion for each kind under both
  `account-managers` values.
- **REST:** `PartyRef` serialization for each kind; the parties endpoint.
- **`AddCommand`/`RemoveCommand`:** group owners are recognised.

## Delivery

Stage 1 ships as a series of PRs in dependency order; the implementation plan
sets their exact boundaries:

1. Party model, V19, entities, mappers, public API bump
2. Payments (`EconomyProvider`, `PartyWallets`, removal of the GOVERNMENT-first rule)
3. Authorization (`ActorContext`, assignment rules, conflict of interest)
4. Commands, group mapping, configuration, display
5. Notifications, inbox, listings, statistics
6. REST

## Later stages (outline)

**Stage 2 — titleholder.** Purchase, offer and bid payments from account and
group parties, gated to authorizers; escrow refunds back to the paying party;
property tax charged through the party's account, with exemptions by party;
WorldGuard ownership for account titleholders by syncing account members as
owners (Treasury has no membership events, so on transfer, on join, periodically
and on command) and for group titleholders by WorldGuard group ownership;
subregion landlords following a non-player titleholder; the one-type-flag rule
on `create freehold`; the UUID-only WorldGuard owner checks in the titleholder
commands replaced by `ActorContext` checks against the titleholder party.

**Stage 3 — tenant.** Rent, renewal, modification and termination payments from
account and group parties, reusing stage 2's authorizer gate and WorldGuard sync;
the owner check in `UnrentCommand` replaced by an `ActorContext` check against
the tenant party.

## Alternatives considered

- **Payout account next to a player landlord.** Rejected: a government account
  may have no player behind it.
- **Stand-in UUID derived from the account id.** Rejected as a workaround; it
  hides the party's type inside a value the database cannot check.
- **Every party is a Treasury account, players by PERSONAL account id.**
  Rejected: WorldGuard ownership and player checks need UUIDs, and Vault
  support would be lost.
- **One `Party` table with nullable columns and a `CHECK` on its shape.** The
  first draft of this spec, and what stage 1 was first built with. Rejected on
  review: it stores the account type list twice, its shape is a three-branch
  check the database can only partly express, and per-kind tables cost
  nothing measurable (three primary-key probes into small cached tables in
  place of one; tens of microseconds on a joined read at 100k contracts).
- **Column pairs per role instead of a `Party` table.** Viable with two kinds;
  with three kinds each role needs four columns and a three-branch check,
  repeated across contracts and history for four roles.
- **`personal:` / `gov:` prefixes, or `-account` command variants.** Rejected
  in favour of a plain name plus a type flag, so everyday player use is
  unchanged.
- **Group mappings in configuration.** Rejected: a file edit can remove a
  mapping still used by a contract. In the database, `unmap` can refuse.
- **Automatic startup migration of legacy government UUIDs.** Rejected in
  favour of a one-time manual SQL fix; no legacy migrator is kept in the code.
- **REST `/v2`.** Rejected: adding `kind` keeps the JSON shape, and a `/v1`
  frozen alongside would have to hide non-player parties. The same holds for
  the new meaning of `landlord` in a listing.
