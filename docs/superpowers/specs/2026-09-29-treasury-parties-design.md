# Treasury Parties — Design

Date: 2026-09-29
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

### Schema (migration V19)

```sql
CREATE TABLE Party (
    partyId          INT PRIMARY KEY AUTO_INCREMENT,
    kind             ENUM ('PERSONAL','BUSINESS','GOVERNMENT','SYSTEM','GROUP') NOT NULL,
    playerUuid       UUID         NULL UNIQUE,
    accountId        INT          NULL UNIQUE,
    groupName        VARCHAR(64)  NULL UNIQUE,
    groupAccountId   INT          NULL,
    groupAccountKind ENUM ('BUSINESS','GOVERNMENT','SYSTEM') NULL,
    CONSTRAINT chk_party_shape CHECK (
        (kind = 'PERSONAL'
            AND playerUuid IS NOT NULL AND accountId IS NULL AND groupName IS NULL
            AND groupAccountId IS NULL AND groupAccountKind IS NULL)
        OR (kind IN ('BUSINESS','GOVERNMENT','SYSTEM')
            AND playerUuid IS NULL AND accountId IS NOT NULL AND groupName IS NULL
            AND groupAccountId IS NULL AND groupAccountKind IS NULL)
        OR (kind = 'GROUP'
            AND playerUuid IS NULL AND accountId IS NULL AND groupName IS NOT NULL
            AND groupAccountId IS NOT NULL AND groupAccountKind IS NOT NULL)
    )
);
```

`groupAccountId` is not unique: several groups may share one account. An
account's own kind is stored because Treasury does not allow it to change
(`updateAccount` mutates only name, authorization, archive state, overdraft and
credit limit) and because `realty-rest` cannot ask Treasury. Display names are
**not** stored; accounts can be renamed, so names are looked up when shown.

Stage 1 replaces two role columns with party foreign keys:

| Table | Old column | New column |
|---|---|---|
| `LeaseholdContract` | `landlordId UUID` | `landlordPartyId INT` |
| `LeaseholdHistory` | `landlordId UUID` | `landlordPartyId INT` |
| `FreeholdContract` | `authorityId UUID` | `authorityPartyId INT` |
| `FreeholdHistory` | `authorityId UUID` | `authorityPartyId INT` |

The migration, in SQL only:

1. Create `Party`.
2. `INSERT INTO Party (kind, playerUuid) SELECT DISTINCT 'PERSONAL', …` over the
   union of the four old columns.
3. Add each new column nullable, fill it with `UPDATE … JOIN Party ON
   playerUuid = <old column>`, then make it `NOT NULL` with a foreign key to
   `Party(partyId)`.
4. Drop `idx_leasehold_contract_landlord` and the old columns; index the new
   columns.

Existing data becomes PERSONAL parties with no change in meaning.

### Entities and mappers

`LeaseholdContractEntity.landlordId()` becomes `landlord(): Party`;
`FreeholdContractEntity.authorityId()` becomes `authority(): Party`; tenant and
titleholder are exposed as `Party` (always `Personal` in stage 1). Every read
joins `Party` on its primary key, and one shared MyBatis result mapping builds
the `Party` value, so no mapper assembles it by hand. A `PartyMapper` offers
find-or-insert by identity, used by every write that assigns a party.

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
events such as termination refunds taking effect.

A new `PartyWallets` class in the paper plugin resolves the account a party
pays from and into:

| Party | Treasury | Vault |
|---|---|---|
| `Personal` | `resolveOrCreatePersonal(uuid)` | the player's balance |
| `Account` | `getAccountById(accountId)` | refused: requires Treasury |
| `Group` | `getAccountById(groupAccountId)` | refused: requires Treasury |

- The GOVERNMENT > PERSONAL > BUSINESS preference in `TreasuryEconomyProvider`
  is removed. A `Personal` party always uses the PERSONAL account.
- A balance check reads the same account the transfer will use.
- An account that Treasury has archived or deleted since assignment, or whose
  type no longer matches the stored kind, makes the payment fail with a clear
  message. This is the only unavoidable runtime failure, and it is outside
  Realty's control.
- Every payment flow stays all-or-nothing, as established by the failed-purchase
  and failed-tenancy work: a payment that fails leaves nothing behind.
- The 2-decimal normalisation stays.

### Accounts requiring authorization

Treasury accounts can have `requiresAuthorization`, and `TransferRequest` has an
`authorizer` field that Realty currently always sends as `null`. Before stage 1
ships, confirm how Treasury handles a transfer out of such an account with no
authorizer. If Treasury rejects it, such accounts **cannot be assigned as a
party** (landlord, authority, or a group's account), and assignment is refused
with a dedicated message. Otherwise a tenant-triggered refund out of that account
could fail, and the unrent would roll back.

## Who acts for a party

The paper plugin builds one `ActorContext` per command, on the database
executor because the lookups do I/O, before calling the backend:

```java
record ActorContext(UUID player,
                    Set<Party> manages,
                    Set<Party> reassigns,
                    boolean bypass)
```

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
  servers.

The backend's identity checks become set membership:
`actorId.equals(lease.landlordId())` becomes `ctx.manages().contains(lease.landlord())`.
Sites changed in stage 1:

- Landlord: `RealtyBackendImpl` (landlord checks around lines 818, 1058, 1119,
  1461), `SetCommandGroup` (line 99), `SignCommand` (line 104),
  `RealtyPaperApiImpl.computeTerminationPlan` (line 525).
- Authority (offers and auctions): `RealtyBackendImpl` around lines 222, 1575,
  1609, 1690, 1725.

When deriving the role in termination, the tenant check stays first, as today.

### Assigning a party

`/realty set landlord` and `/realty set authority` require the **current** party
to be in the actor's `reassigns`. For non-admins, they also require the **new**
party to be in `manages`: nobody can hand a role to an account or group they do
not belong to. Admins with the `*.others` permission bypass both.

### Conflict of interest

"The authority cannot buy, bid on or make offers on its own land, and cannot be
invited as an agent" (`RealtyBackendImpl` around lines 648, 281, 1655, 114)
becomes: refused when the authority is in the actor's `manages`.

- Permission `realty.bypass.conflict-of-interest` lifts this membership-based
  rule.
- A party dealing with *itself* (Steve buying from Steve, GovSecurity paying
  GovSecurity) stays refused even with the permission.

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
`--group` changes its meaning; the flags are mutually exclusive.

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
| `/realty set landlord <name> [flags] [region]` | any party kind; assignment rules above |
| `/realty set authority <name> [flags] [region]` | same |
| `/realty create\|register leasehold --landlord <name>` | type flags apply to the landlord |
| `/realty create\|register freehold --authority <name>` | type flags apply to the authority; `--titleholder` stays player-only |
| `/realty set titleholder`, `set tenant`, `transfer` | unchanged until stages 2 and 3 |
| `/realty add\|remove <name> [--group]` | `g:` prefix removed; `--group` adds/removes a WorldGuard member group, no mapping needed |
| `/realty group map <group> <account> --government\|--business\|--system` | creates the group's party row or changes its account |
| `/realty group unmap <group>` | refused while any contract uses the group |
| `/realty group list` | each group, its account, and how many contracts use it |
| `/realty list [<name> flags]` | shows an account's or group's portfolio |

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
member groups as `police (group)` instead of `g:police`.

### Messages and permissions

New message keys: unknown account; archived account; account type does not
match flag; type flags require Treasury; group not mapped; not allowed to
assign or reassign this party; account requires authorization (if the Treasury
check makes that rule necessary); more than one type flag; group still in use
(unmap).

New permissions: `realty.command.group`, `realty.bypass.conflict-of-interest`.

### Unchanged in stage 1

- Subregion landlords still follow the parent titleholder, who is a player
  until stage 2; `/realty set landlord` can change a child's landlord afterwards.
- The AreaShop importer assigns players; its fallbacks read the new config
  defaults.
- Property tax: titleholders are still players.

## Configuration

`settings.yml`:

```yaml
account-managers: members        # members | authorizers

default-freehold-authority: { name: GovSecurity, type: government }
default-leasehold-landlord: { name: GovSecurity, type: government }
# type omitted → name is a player; `uuid: <uuid>` may be given instead of `name` for a player
```

The `default-*-uuid` keys and the all-zeros default are removed. Every default
must resolve at startup; if one does not, startup logs an error and the commands
that depend on it refuse to run rather than fall back.

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
- New endpoint `GET /v1/parties/{kind}/{id}/regions`, mirroring
  `/v1/players/regions`.
- Account names are resolved through the query-service module, which runs in the
  game server and can call Treasury, exactly as it already resolves player
  names. Group names come from `Party`.
- The OpenAPI document renames the type to `PartyRef` and states the `id` rule.
- `realty-rest`'s expected schema version is bumped to V19; plugin and REST
  deploy together.

The JSON shape does not change, so under the REST versioning policy this is not
a breaking change. The meaning of `id` widens, which the release notes call out
for API consumers; only data created after this ships can carry a non-UUID `id`.

## Public API

- Events and `RealtyPaperApi` expose `Party getLandlord()`, `getAuthority()`,
  `getTenant()` and `getTitleholder()`, and the methods that assign a party take
  `Party`.
- The old `get…Id()` methods are deprecated: they return the UUID for a
  `Personal` party and `null` otherwise. They are removed in a later major
  version.
- `realty-paper-api` and `realty-backend-api` take one major version bump in
  stage 1.

## Upgrade

Release-note runbook:

1. Stop the server; deploy the plugin and `realty-rest` together; start with the
   whitelist on. V19 converts all existing parties to PERSONAL.
2. For each legacy government UUID, run the manual fix: insert the account's
   `Party` row (`kind = 'GOVERNMENT'`, `accountId = <id>`), then repoint that
   UUID's party id in `LeaseholdContract`, `LeaseholdHistory`,
   `FreeholdContract` and `FreeholdHistory` to the new row.
3. `/realty group map` any groups that should become parties.
4. Replace the `default-*-uuid` keys with the new party settings.
5. Lift the whitelist.

The whitelist keeps player-triggered payments from reaching a legacy UUID's
PERSONAL account between step 1 and step 2. A scheduled termination refund can
still fire in that window; it would be paid out of the legacy PERSONAL account
and can be corrected with a manual Treasury transfer.

The release notes also call out: the `g:` prefix is removed; the API major
bump; the widened REST `id`.

### Treasury facts this design relies on

- A UUID owns at most one GOVERNMENT account (the manual fix maps each legacy
  UUID to exactly one account). Stated by the operator; not visible in the API.
- An account's type cannot change after creation (`updateAccount` does not
  mutate it).
- The behaviour of `requiresAuthorization` accounts without an authorizer — to
  be verified before stage 1 ships (see *Payments*).

## Testing

- **Migration:** V19 against seeded data in the `MariaSchemaMigratorTest` setup:
  every old UUID becomes one PERSONAL party, repointed rows match their originals,
  and `chk_party_shape` rejects malformed rows.
- **Mappers:** the shared result mapping builds each party kind; `PartyMapper`
  find-or-insert is idempotent.
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
  frozen alongside would have to hide non-player parties.
