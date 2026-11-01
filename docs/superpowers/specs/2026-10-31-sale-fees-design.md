# Sale fees: design

Status: agreed in discussion, section by section, on 2026-10-31. Awaiting review of this written spec.

Target release: 2.3.0, in one release. This replaces the planned government "portfolio levy": the levy becomes one kind of sale fee.

## Goal

A freehold sale can carry fees. A fee is a percentage of the sale price paid to someone other than the seller. Either the buyer pays it on top of the price, or it is taken from the seller's proceeds.

Fees come from two sources:

- **Levies**, defined in `fees.yml` by the server owner. A server can define several, for example a national levy and a levy for each town. Each plot uses one levy at most, chosen by its authority. A levy is usually a buyer-paid fee that rises with how many plots the buyer already holds.
- **Player fees**, attached to a region by someone with a role on it. An agent's commission is one: a percentage paid to the agent on a sale they ran.

Fees apply to every freehold sale: auctions, offers and `/realty buy`.

This release also fixes a bug: when an auction or offer payment deadline passes, the buyer is told their payments were refunded, but no money moves. The seller keeps them.

## Decisions

| Question | Decision |
|---|---|
| Government only, or general? | General. The levy is one fee among others. |
| A town levying instead of the central government | `fees.yml` defines several named levies. Each plot uses one: the one its authority chose, or the default. |
| Who chooses a plot's levy | The plot's authority, for one plot or all its plots. Admins anywhere. |
| Who sets a levy's rates | Only the server owner, in `fees.yml`. |
| Who pays a fee? | Set per fee: the buyer on top, or the seller from proceeds. |
| Who can attach a player fee? | Anyone with a role on the region: the authority (through its managers), the titleholder, or an agent. |
| Who receives a player fee? | Always whoever attached it. |
| Seller protection | A seller-paid fee attached by someone other than the seller needs the seller's approval. |
| Buyer protection | Every fee is shown before a bid, offer or buy. |
| Cap on total fees | None. |
| Amount | A percentage of the price. A levy chooses the percentage from tiers by the buyer's plot count. A player fee has one percentage. |
| Which sales | Auctions, offers and `/realty buy`. |
| When an agent's fee is paid | Only on a sale that agent ran: their auction, or an offer they accepted. |
| How fees are fixed | Locked into fee lines when the sale locks. Later changes do not affect locked sales. |
| Where instalments wait | In a holding (escrow) account until the sale finishes. |
| Holding account | Realty's own Treasury SYSTEM account. On a Vault-only server, a player account an admin names. |
| Rounding | Each fee rounds down to the cent. The seller receives the remainder. |
| A fee paid to the buyer | Skipped. |
| Release | One release, 2.3.0. |

## 1. How the money moves

### The holding account

Buyers' instalments wait in a holding account until the sale finishes. It belongs to neither the buyer nor the seller.

- **With Treasury**, it is Realty's own SYSTEM account. Treasury allows one SYSTEM account per plugin and describes them as the accounts plugins route money through. ChestShop and the Vault bridge have theirs. Realty finds or creates its account at startup, the way ChestShop does, owned by a fixed Realty UUID. Unlike ChestShop's, it does not allow overdraft, so a bug cannot pay out money the account does not hold.
- **With Vault only**, Vault can move money only between players, so an admin names a player account in `settings.yml`, under the default parties, as `escrow`. Until one is named, an auction cannot move from bidding to payment and an offer cannot be accepted. Both refuse with a clear message, and Realty logs a warning at startup. Bidding and placing offers still work.

### Each step

1. **Instalment.** Each auction or offer instalment is paid from the buyer to the holding account, not to the seller.
2. **Completion.** When the final instalment arrives, the holding account pays out:
   - to the seller, the price minus seller-paid fees;
   - to each fee recipient, their fee line.
3. **`/realty buy`.** A buy has no instalments. The buyer pays the seller and each fee recipient directly, in one go.
4. **What the buyer owes.** The price plus buyer-paid fees. A bid of 1,000 with a 5% levy owes 1,050.
5. **Expiry.** When a payment deadline passes, the holding account pays everything the buyer has paid back to them. The auction then moves to the next bidder as before.

### When something fails

- **A refund that fails** is recorded and retried. It is never dropped.
- **A payout that fails** after the buyer has paid in full does not stop the sale. The money is in the holding account, so ownership moves to the buyer. The failed payout, for example to an archived account, is recorded as owed and retried.
- **An instalment that fails** is rolled back as today.
- **A holding account that cannot be used**, for example because it is archived or no escrow player is set, pauses every payment that pays into it. Each minute its deadline is moved to the full payment duration from now, so no buyer is expired by an outage they cannot pay through. A buyer who tries to pay is told payments are paused. No auction moves to payment and no offer can be accepted meanwhile. Admins get one error when it becomes unusable and one notice when it recovers. Payments that began before sale fees pay the seller and are not paused.
- Ownership no longer waits for every payout to succeed. A buyer who has paid in full is not held up by an unrelated account problem.

### Payments in progress at the upgrade

Instalments paid before 2.3.0 went to the seller directly and carry no fees. Those payment records are flagged at the upgrade and finish under the old rules: instalments to the seller, no fees, no refund from the holding account.

## 2. Where fees come from, and how they are locked

### Levies: `fees.yml`

```yaml
levies:
  - name: national-levy
    enabled: false
    default: true           # used by plots that have not chosen a levy
    paid-by: buyer          # buyer | seller
    recipient: authority    # authority, or a named party like the default parties
    authority-kind: government   # optional: only plots with this kind of authority can use it
    rules:                  # first match wins, like taxes.yml
      - match:
          any: [residential]
        tiers:
          - { from-plots: 0, percent: 0 }
          - { from-plots: 3, percent: 5 }
          - { from-plots: 10, percent: 15 }
  - name: townville-levy
    enabled: true
    paid-by: buyer
    recipient: authority
    rules:
      - match:
          any: [residential]
        tiers:
          - { from-plots: 0, percent: 2 }
          - { from-plots: 5, percent: 8 }
```

Choosing a plot's levy:

| Command | Who | What it does |
|---|---|---|
| `/realty levy set <levy> [region] --as authority` | Authority managers | Sets the levy for one plot. |
| `/realty levy set <levy> --all --as authority` | Authority managers | Sets the levy for every plot of that authority, including plots it gains later. |
| `/realty levy clear [region] --as authority` | Authority managers | Clears one plot's choice. It then uses the authority's choice, or the default. |
| `/realty levy clear --all --as authority` | Authority managers | Clears the authority's choice. Its plots then use their own choice, or the default. |
| `/realty levy list` | Anyone | Every levy in `fees.yml` with its rates. |

- A plot's levy is, in order: the plot's own choice, its authority's choice, then the levy marked `default`. At most one levy applies to a sale.
- Admins, with a bypass permission, can set or clear a levy on any plot or for any authority.
- The levy applies when it is enabled, its `authority-kind` allows the plot's authority, and one of its rules matches the plot's tags. Otherwise the plot pays no levy. Realty does not fall back to another levy.
- With `recipient: authority`, the money goes to the plot's authority, so a town that chooses its own levy is paid itself.
- A town chooses a levy but cannot change its rates. A town that wants different rates asks the server owner to add a levy.
- A choice naming a levy removed from `fees.yml` is ignored, so the plot falls back as if it had no choice. Realty logs a warning at startup for each such choice. The choice is kept, so restoring the levy restores it.
- At most one levy may be marked `default`. Realty refuses to load a `fees.yml` with more than one.
- The plot count is the number of titles the buyer holds personally, not counting the region being bought. This is the count `taxes.yml` uses.
- The shipped file contains the national levy as the default, turned off.

### Player fees

Kept per region in a new table: the region, the recipient party, the side that pays, the percentage, who set it, and whether it is approved.

| Command | Who | What it does |
|---|---|---|
| `/realty fee set <percent> <buyer\|seller> [region] [--as authority]` | Authority managers, titleholder, agents | Sets your fee on the region, paid to you. Running it again replaces it. |
| `/realty fee remove [region] [--as authority]` | Whoever set the fee | Removes your fee. |
| `/realty fee list [region]` | Anyone | Every fee that would apply, the levy and player fees, with its status. |
| `/realty fee approve <recipient> [region]` | The seller | Activates a seller-paid fee someone else set. |
| `/realty fee reject <recipient> [region]` | The seller | Refuses it, which removes it. |

- One fee per recipient per region.
- "Paid to you" means your personal account. With `--as authority`, it means the authority's account. Without the flag, a player who is both is paid personally.
- A buyer-paid fee applies as soon as it is set. A seller-paid fee set by anyone other than the seller waits for approval, and the seller is notified.
- The seller is the titleholder, or the authority when nobody holds the title.
- An agent's fee applies only to sales that agent ran. It is removed when they stop being an agent.
- Accepting an offer now records who accepted it. Auctions already record who created them.

### Locking

One fee resolver works out the fee lines for a sale. They are saved in the `SaleFee` table, attached to the payment, at the moment the sale locks:

- a `/realty buy`, in the same transaction as the reservation;
- the end of bidding, for the winner;
- the handover to a runner-up, again, because the buyer and their plot count changed;
- the acceptance of an offer.

Each line holds the recipient, the side, the rate, the amount, the source (the levy's name, or for a player fee its role label, such as "agent fee"), and whether it has been paid out. Changing a plot's levy does not affect a sale already locked.

Each fee rounds down to the cent. The seller receives whatever remains, so the payouts always add up exactly to what the buyer paid. A fee whose recipient is the buyer is left out.

### Preview

`previewFees(region, buyer, price)` runs the same resolver without saving. Commands use it to show fees before a bid, offer or buy. Plugins such as DiscordUtilities can use it too.

## 3. What players see

**Before committing**, for example `/realty bid 1000` on a government plot with a 5% levy and a 3% agent fee:

```
Bidding 1,000 on c404. If you win you will owe:
  Price            1,000.00
  Government levy     50.00  (5%, you hold 4 plots)
  Total            1,050.00
The seller also pays: agent fee 30.00 (3%, to Servalot)
```

- `/realty offer send` and `/realty buy` show the same breakdown.
- `/realty buy` asks for confirmation when any fee applies.

**While paying:** `Paid 400 towards c404. Paid 700 of 1,050. 350 left by <deadline>.`

**At completion:**

- The buyer is told the title is theirs.
- The seller: `c404 sold for 1,000. You received 970 after fees.`
- Each fee recipient: `You were paid 30 (agent fee) from the sale of c404.`

**At expiry:** `Your payment deadline for c404 passed. 700 was refunded.` If the refund failed, it says the refund is pending and will be retried.

**`/realty info`** gains a "Fees on sale" line: each fee with its rate and who pays it. The levy shows its name and range, for example "townville-levy 2–8% by plots held".

## 4. API and events

New types in `realty-backend-api`:

```java
record FeeLine(String source, Party recipient, FeeSide side, double percent, double amount) {}
enum FeeSide { BUYER, SELLER }
record FeeQuote(double price, List<FeeLine> lines, double buyerTotal, double sellerReceives) {}
```

A `FeeLine`'s `source` is a levy's name for a levy, and the role label of a fee a player set, such as "agent fee".

New methods on `RealtyPaperApi`, returning `CompletableFuture` like the rest:

- `previewFees(region, buyerId, price)` returns a `FeeQuote` and saves nothing.
- `setFee(region, ctx, percent, side, asAuthority)`, `removeFee(region, ctx, asAuthority)`, `listFees(region)`, `approveFee(region, ctx, recipient)`, `rejectFee(region, ctx, recipient)`. Each returns a sealed result in the existing style: `Success`, `NotAllowed`, `NoFreehold`, `NoChange` and so on.
- `setRegionLevy(region, ctx, levy)`, `clearRegionLevy(region, ctx)`, `setAuthorityLevy(authority, ctx, levy)`, `clearAuthorityLevy(authority, ctx)`, and `listLevies()`. The set and clear methods return sealed results in the same style, with `UnknownLevy` for a name not in `fees.yml`.

Changed results:

- `BuyResult.Success`, `PayBidResult.FullyPaid` and `PayOfferResult.FullyPaid` gain the `FeeQuote`.
- `PayBidResult.Success` and `PayOfferResult.Success` report amounts against the total the buyer owes.
- `ActiveAuction` and `OfferListing` gain a nullable `FeeQuote`, set once the sale has locked.
- New results for a sale that cannot move to payment because no holding account is configured.

Changed events:

- `RegionBoughtEvent`, `AuctionWonPurchaseEvent` and `OfferPurchaseCompletedEvent` gain `getFees()`.
- `AuctionPaymentExpiredEvent` and `OfferPaymentExpiredEvent` gain `isRefundPaid()`.

New events:

- `FeeChangeEvent`, cancellable, before a fee is set or replaced; `FeeChangedEvent` after.
- `FeeRemovedEvent`, `FeeApprovedEvent`, `FeeRejectedEvent`.
- `LevyChangeEvent`, cancellable, before a plot's or an authority's levy choice is set or cleared; `LevyChangedEvent` after.
- `FeePaidEvent` for each payout, including a retry that succeeds.
- `RefundPaidEvent` when a refund succeeds, including on a retry.

Compatibility: adding fields to records changes their constructors and record patterns. A plugin built against 2.2 that builds or deconstructs these records must be rebuilt. The release notes say so.

## 5. Database

One migration, V21.

New tables:

- **`RegionFee`**: player fees. Region, recipient party, side, percent, set by (player UUID), approved, created at. One row per recipient per region. Deleted with the region.
- **`RegionLevy`**: a plot's levy choice. Region, levy name, set by, set at. Deleted with the region.
- **`AuthorityLevy`**: an authority's levy choice. Authority party, levy name, set by, set at.
- **`SaleFee`**: locked fee lines. Payment kind (buy, bid or offer), payment id, region, source, recipient party, side, percent, amount, status (pending, paid or failed), attempts, last error.
- **`PendingRefund`**: refunds that failed. Buyer, amount, region, reason, attempts, last error. Deleted once paid.

Changed tables:

- `FreeholdContractBidPayment` and `FreeholdContractOfferPayment` gain a total-due column and a flag for payments made under the old rules. Their payment check moves from the price to the total due.
- `FreeholdContractOfferPayment` records who accepted the offer.

Retries: the scheduler that clears expired payments also retries failed payouts and pending refunds. After a set number of attempts it stops and logs an error for an admin. The records stay.

History: the `FreeholdHistory` row for a sale still records the price. Fee lines live in `SaleFee`. Market totals and the last-sold price stay based on the price.

Upgrade: the plugin and `realty-rest` together, as in 2.2. `realty-rest` expects schema version 21.

## 6. Testing

- The fee resolver: tag matching, tiers by plot count, the authority-kind filter, choosing the levy (the plot's choice, then the authority's, then the default; a disabled or non-matching levy means no levy; a removed levy is ignored), seller approval, agent fees only on their own sales, a fee paid to the buyer skipped, rounding down with the remainder to the seller.
- Locking: a fee set after the lock does not change a locked sale. A runner-up's fees are worked out again.
- Money: each instalment reaches the holding account. The completion payout pays the seller and each recipient the right amounts. An expiry refunds from the holding account. A failed payout or refund is recorded and retried.
- Without a holding account on Vault: moving to payment refuses, bidding and offers still work.
- The upgrade: a payment in progress from before 2.3.0 finishes under the old rules.
- A migration test from schema version 20.
