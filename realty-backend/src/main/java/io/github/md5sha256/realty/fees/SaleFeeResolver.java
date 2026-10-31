package io.github.md5sha256.realty.fees;

import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyCatalog;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.PartyKind;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RegionFeeEntity;
import io.github.md5sha256.realty.database.entity.SaleFeeEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Works out the fees a sale carries and saves them as {@code SaleFee} lines. Every method runs in
 * the caller's transaction. Internal to the backend: only {@code RealtyBackendImpl} uses it, and
 * it is public only because that class lives in another package.
 */
public final class SaleFeeResolver {

    /** The {@code SaleFee.lineKind} of a fee. */
    public static final String LINE_FEE = "fee";
    /** The {@code SaleFee.lineKind} of the seller's proceeds. */
    public static final String LINE_PROCEEDS = "proceeds";

    private static final String STATUS_LOCKED = "locked";
    private static final String STATUS_PAID = "paid";
    /** The {@code RegionFee.role} of a fee set by a sanctioned auctioneer. */
    private static final String ROLE_AGENT = "agent";

    private final Supplier<LevyCatalog> levies;

    /** @param levies the levies the server defines, read on each use so that a reload takes effect */
    public SaleFeeResolver(@NotNull Supplier<LevyCatalog> levies) {
        this.levies = levies;
    }

    /**
     * The fees on a sale of {@code region} to {@code buyerId} at {@code price}, as they stand now.
     *
     * @param ranBy the agent who ran the sale; an agent's fee applies only to a sale it ran, so
     *              {@code null} applies no agent fee
     */
    public @NotNull FeeQuote resolve(@NotNull SqlSessionWrapper wrapper,
                                     @NotNull RealtyRegionEntity region,
                                     @NotNull FreeholdContractEntity freehold,
                                     @NotNull UUID buyerId,
                                     double price,
                                     @Nullable UUID ranBy) {
        Set<String> tags = new HashSet<>();
        for (String tag : wrapper.regionTagMapper().selectTagIdsByRegionId(region.worldGuardRegionId())) {
            tags.add(tag.toLowerCase(Locale.ROOT));
        }
        int buyerPlots = wrapper.realtyRegionMapper().countRegionsByTitleHolder(buyerId);

        Party authority = freehold.authority();
        FeeCalculator.LevyInput levyInput = null;
        LevyDefinition levy = resolveLevy(wrapper, this.levies.get(), region.realtyRegionId(), authority);
        if (levy != null && levyFitsAuthority(levy, authority)) {
            levyInput = new FeeCalculator.LevyInput(levy, levy.recipient() != null ? levy.recipient() : authority);
        }

        List<FeeCalculator.PlayerFeeInput> playerFees = new ArrayList<>();
        for (RegionFeeEntity fee : wrapper.regionFeeMapper().selectByRegion(region.realtyRegionId())) {
            if (!fee.approved() || (ROLE_AGENT.equals(fee.role()) && !fee.setBy().equals(ranBy))) {
                continue;
            }
            Party recipient = wrapper.partyMapper().selectById(fee.recipientPartyId());
            if (recipient == null) {
                continue;
            }
            playerFees.add(new FeeCalculator.PlayerFeeInput(fee.role() + " fee", recipient, fee.side(), fee.percent()));
        }
        return FeeCalculator.quote(price, buyerId, levyInput, tags, buyerPlots, playerFees);
    }

    /**
     * Saves the quote as the sale's locked lines: one per fee, and one for the seller's proceeds
     * when there are any. They are paid out once the sale is marked completed.
     *
     * @param saleKind {@code bid} or {@code offer}
     * @param saleRef  the bidId or offerId
     */
    public void lockFees(@NotNull SqlSessionWrapper wrapper,
                         @NotNull String saleKind,
                         @Nullable Integer saleRef,
                         @NotNull RealtyRegionEntity region,
                         @NotNull UUID buyerId,
                         @NotNull Party seller,
                         @NotNull FeeQuote quote) {
        insertLines(wrapper, saleKind, saleRef, region.realtyRegionId(), region.worldGuardRegionId(), region.worldId(),
                buyerId, seller, quote, STATUS_LOCKED, null);
    }

    /**
     * Saves the quote as the lines of a sale whose money has already moved, all paid.
     *
     * @param realtyRegionId the region's id, or {@code null} if it is no longer registered
     */
    public void recordPaidFees(@NotNull SqlSessionWrapper wrapper,
                               @NotNull String saleKind,
                               @Nullable Integer realtyRegionId,
                               @NotNull String regionName,
                               @NotNull UUID worldId,
                               @NotNull UUID buyerId,
                               @NotNull Party seller,
                               @NotNull FeeQuote quote) {
        insertLines(wrapper, saleKind, null, realtyRegionId, regionName, worldId, buyerId, seller, quote,
                STATUS_PAID, LocalDateTime.now());
    }

    private static void insertLines(@NotNull SqlSessionWrapper wrapper,
                                    @NotNull String saleKind,
                                    @Nullable Integer saleRef,
                                    @Nullable Integer realtyRegionId,
                                    @NotNull String regionName,
                                    @NotNull UUID worldId,
                                    @NotNull UUID buyerId,
                                    @NotNull Party seller,
                                    @NotNull FeeQuote quote,
                                    @NotNull String status,
                                    @Nullable LocalDateTime completedAt) {
        // The party rows are made here, in the sale's transaction. That is safe: the sale's own
        // row lock already serializes the region.
        for (FeeLine line : quote.lines()) {
            int recipientPartyId = wrapper.partyMapper().findOrInsert(line.recipient());
            wrapper.saleFeeMapper().insert(new SaleFeeEntity(0, saleKind, saleRef, realtyRegionId, regionName,
                    worldId, buyerId, LINE_FEE, line.source(), recipientPartyId, line.side(), line.percent(),
                    line.amount(), status, completedAt, 0, null, null));
        }
        if (quote.sellerReceives() > 0) {
            int sellerPartyId = wrapper.partyMapper().findOrInsert(seller);
            double percent = quote.price() > 0
                    ? BigDecimal.valueOf(quote.sellerReceives()).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(quote.price()), 4, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            wrapper.saleFeeMapper().insert(new SaleFeeEntity(0, saleKind, saleRef, realtyRegionId, regionName,
                    worldId, buyerId, LINE_PROCEEDS, LINE_PROCEEDS, sellerPartyId, FeeSide.SELLER, percent,
                    quote.sellerReceives(), status, completedAt, 0, null, null));
        }
    }

    /**
     * The quote a sale locked, rebuilt from its lines.
     *
     * @param price    the sale price
     * @param totalDue what the buyer owes in all, as the payment row holds it
     */
    public static @NotNull FeeQuote lockedQuote(@NotNull SqlSessionWrapper wrapper,
                                                @NotNull String saleKind,
                                                int saleRef,
                                                double price,
                                                double totalDue) {
        List<FeeLine> lines = new ArrayList<>();
        Double proceeds = null;
        BigDecimal sellerFees = BigDecimal.ZERO;
        for (SaleFeeEntity row : wrapper.saleFeeMapper().selectBySale(saleKind, saleRef)) {
            if (LINE_PROCEEDS.equals(row.lineKind())) {
                proceeds = row.amount();
                continue;
            }
            Party recipient = wrapper.partyMapper().selectById(row.recipientPartyId());
            if (recipient == null) {
                throw new IllegalStateException("no Party row " + row.recipientPartyId() + " for sale fee " + row.saleFeeId());
            }
            lines.add(new FeeLine(row.source(), recipient, row.side(), row.percent(), row.amount()));
            if (row.side() == FeeSide.SELLER) {
                sellerFees = sellerFees.add(BigDecimal.valueOf(row.amount()));
            }
        }
        double sellerReceives = proceeds != null
                ? proceeds
                : BigDecimal.valueOf(price).subtract(sellerFees).doubleValue();
        return new FeeQuote(price, lines, totalDue, sellerReceives);
    }

    /**
     * The levy a region uses: its own choice, else its authority's choice, else the default. A
     * choice naming a levy the catalog no longer has is skipped and left in place.
     */
    public static @Nullable LevyDefinition resolveLevy(@NotNull SqlSessionWrapper wrapper,
                                                       @NotNull LevyCatalog catalog,
                                                       int realtyRegionId,
                                                       @NotNull Party authority) {
        String regionChoice = wrapper.levyChoiceMapper().selectRegionLevy(realtyRegionId);
        if (regionChoice != null) {
            Optional<LevyDefinition> levy = catalog.find(regionChoice);
            if (levy.isPresent()) {
                return levy.get();
            }
        }
        Integer authorityPartyId = wrapper.partyMapper().findId(authority);
        if (authorityPartyId != null) {
            String authorityChoice = wrapper.levyChoiceMapper().selectAuthorityLevy(authorityPartyId);
            if (authorityChoice != null) {
                Optional<LevyDefinition> levy = catalog.find(authorityChoice);
                if (levy.isPresent()) {
                    return levy.get();
                }
            }
        }
        return catalog.defaultLevy().orElse(null);
    }

    /** Whether {@code authority} is of the kind the levy is limited to, if it is limited to one. */
    public static boolean levyFitsAuthority(@NotNull LevyDefinition levy, @NotNull Party authority) {
        return levy.authorityKind() == null || levy.authorityKind() == authorityKind(authority);
    }

    /**
     * The kind a levy's {@code authorityKind} is matched against: a group counts as the kind of
     * the account behind it.
     */
    public static @NotNull PartyKind authorityKind(@NotNull Party authority) {
        return switch (authority) {
            case Party.Personal _ -> PartyKind.PERSONAL;
            case Party.Account account -> PartyKind.of(account.kind());
            case Party.Group group -> PartyKind.of(group.accountKind());
        };
    }
}
