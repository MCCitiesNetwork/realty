package io.github.md5sha256.realty.fees;

import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Works out the fees on a sale. The levy comes first, then player fees in the order given.
 * Each fee is rounded down to the cent, and seller-side fees are cut so the seller never owes
 * more than the price.
 */
public final class FeeCalculator {

    private FeeCalculator() {
    }

    /**
     * @param levy      the levy to charge.
     * @param recipient who receives the levy; already resolved, so it is the authority when the
     *                  levy names no recipient.
     */
    public record LevyInput(@NotNull LevyDefinition levy, @NotNull Party recipient) {}

    /**
     * A player fee that already applies to this sale.
     *
     * @param recipient who receives the fee.
     * @param side      who pays it.
     * @param percent   the percentage of the price.
     */
    public record PlayerFeeInput(@NotNull Party recipient, @NotNull FeeSide side, double percent) {}

    /**
     * @param price       the sale price.
     * @param buyerId     the buyer; fees to the buyer are skipped.
     * @param levy        the levy to charge, or {@code null} for none.
     * @param regionTags  the region's tags, lower-cased.
     * @param buyerPlots  how many plots the buyer holds.
     * @param playerFees  the player fees that apply, ordered by creation time.
     * @return the fee lines and both totals.
     */
    public static @NotNull FeeQuote quote(
            double price,
            @NotNull UUID buyerId,
            @Nullable LevyInput levy,
            @NotNull Set<String> regionTags,
            int buyerPlots,
            @NotNull List<PlayerFeeInput> playerFees
    ) {
        BigDecimal priceAmount = BigDecimal.valueOf(price);
        BigDecimal sellerRemainder = priceAmount;
        BigDecimal buyerExtra = BigDecimal.ZERO;
        List<FeeLine> lines = new ArrayList<>();

        List<Candidate> candidates = new ArrayList<>();
        if (levy != null) {
            OptionalDouble percent = levy.levy().percentFor(regionTags, buyerPlots);
            if (percent.isPresent()) {
                candidates.add(new Candidate(levy.levy().name(), levy.recipient(),
                        levy.levy().paidBy(), percent.getAsDouble()));
            }
        }
        for (PlayerFeeInput fee : playerFees) {
            candidates.add(new Candidate(fee.recipient().toString(), fee.recipient(), fee.side(), fee.percent()));
        }

        for (Candidate c : candidates) {
            if (!(c.percent > 0) || Party.playerUuidOf(c.recipient).filter(buyerId::equals).isPresent()) {
                continue;
            }
            BigDecimal amount = priceAmount.multiply(BigDecimal.valueOf(c.percent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
            if (c.side == FeeSide.SELLER) {
                amount = amount.min(sellerRemainder);
                sellerRemainder = sellerRemainder.subtract(amount);
            } else {
                buyerExtra = buyerExtra.add(amount);
            }
            if (amount.signum() == 0) {
                continue;
            }
            lines.add(new FeeLine(c.source, c.recipient, c.side, c.percent, amount.doubleValue()));
        }

        if (lines.isEmpty()) {
            return FeeQuote.none(price);
        }
        return new FeeQuote(price, lines,
                priceAmount.add(buyerExtra).doubleValue(),
                sellerRemainder.doubleValue());
    }

    private record Candidate(String source, Party recipient, FeeSide side, double percent) {}
}
