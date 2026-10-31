package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.CurrencyFormatter;
import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/**
 * Lays out the fees on a sale for the buyer: the price, each fee the buyer pays, the total, then
 * the fees the seller pays out of the price.
 */
public final class FeeQuoteFormatter {

    private FeeQuoteFormatter() {}

    /**
     * @param buyerPlots how many titles the buyer holds, shown on a levy line, as the levy's rate
     *                   depends on it
     * @param names      the display name of a fee's recipient
     * @return the breakdown, one line per row, or {@link Component#empty()} when the sale has no fees
     */
    public static @NotNull Component breakdown(@NotNull FeeQuote quote,
                                               int buyerPlots,
                                               @NotNull MessageContainer messages,
                                               @NotNull Function<Party, String> names) {
        if (quote.lines().isEmpty()) {
            return Component.empty();
        }
        TextComponent.Builder builder = Component.text();
        builder.append(messages.messageFor(MessageKeys.FEES_BREAKDOWN_PRICE,
                Placeholder.unparsed("amount", CurrencyFormatter.format(quote.price()))));
        for (FeeLine line : quote.buyerLines()) {
            String key = line.isLevy() ? MessageKeys.FEES_BREAKDOWN_LEVY_LINE : MessageKeys.FEES_BREAKDOWN_BUYER_LINE;
            builder.appendNewline().append(messages.messageFor(key,
                    Placeholder.unparsed("source", line.source()),
                    Placeholder.unparsed("amount", CurrencyFormatter.format(line.amount())),
                    Placeholder.unparsed("percent", percent(line.percent())),
                    Placeholder.unparsed("plots", buyerPlots == 1 ? "1 plot" : buyerPlots + " plots")));
        }
        builder.appendNewline().append(messages.messageFor(MessageKeys.FEES_BREAKDOWN_TOTAL,
                Placeholder.unparsed("amount", CurrencyFormatter.format(quote.buyerTotal()))));
        List<FeeLine> sellerLines = quote.sellerLines();
        if (!sellerLines.isEmpty()) {
            TextComponent.Builder lines = Component.text();
            for (int i = 0; i < sellerLines.size(); i++) {
                FeeLine line = sellerLines.get(i);
                if (i > 0) {
                    lines.append(Component.text(", "));
                }
                lines.append(messages.messageFor(MessageKeys.FEES_BREAKDOWN_SELLER_LINE,
                        Placeholder.unparsed("source", line.source()),
                        Placeholder.unparsed("amount", CurrencyFormatter.format(line.amount())),
                        Placeholder.unparsed("percent", percent(line.percent())),
                        Placeholder.unparsed("recipient", names.apply(line.recipient()))));
            }
            builder.appendNewline().append(messages.messageFor(MessageKeys.FEES_BREAKDOWN_SELLER_PAYS,
                    Placeholder.component("lines", lines.build())));
        }
        return builder.build();
    }

    /** A percentage with up to two decimals and no trailing zeros: {@code 3}, {@code 2.5}. */
    public static @NotNull String percent(double percent) {
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
