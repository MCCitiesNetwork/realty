package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The fees a sale would carry now, with the buyer's plot count that a levy's rate depends on.
 *
 * @param quote      the fees, from {@link RealtyPaperApi#previewFees}
 * @param buyerPlots how many titles the buyer holds
 */
public record FeePreview(@NotNull FeeQuote quote, int buyerPlots) {

    /** Looks up the quote and the plot count together, on the database executor. */
    public static @NotNull CompletableFuture<FeePreview> of(@NotNull RealtyPaperApi api,
                                                            @NotNull WorldGuardRegion region,
                                                            @NotNull UUID buyerId,
                                                            double price) {
        return api.previewFees(region, buyerId, price)
                .thenCombine(api.listOwnedRegions(Party.personal(buyerId), 1, 0),
                        (quote, owned) -> new FeePreview(quote, owned.totalCount()));
    }

    public boolean hasFees() {
        return !quote.lines().isEmpty();
    }

    /**
     * {@code message}, followed by {@code headerKey} and the breakdown when the sale has fees;
     * {@code message} alone when it has none.
     */
    public @NotNull Component appendTo(@NotNull Component message,
                                       @NotNull String headerKey,
                                       @NotNull MessageContainer messages,
                                       @NotNull PartyNames names) {
        if (!hasFees()) {
            return message;
        }
        return message.appendNewline()
                .append(messages.messageFor(headerKey))
                .appendNewline()
                .append(FeeQuoteFormatter.breakdown(quote, buyerPlots, messages, names::display));
    }
}
