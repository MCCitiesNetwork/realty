package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.util.PartyNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The fees a sale would carry now, with the buyer's plot count that a levy's rate depends on.
 *
 * @param quote          the fees, from {@link RealtyPaperApi#previewFees}
 * @param buyerPlots     how many titles the buyer holds
 * @param agentFeesIfRun the approved agent fees that apply only if their agent runs the sale, as
 *                       when an agent accepts an offer; the quote cannot hold them, because who
 *                       accepts is not known yet
 */
public record FeePreview(@NotNull FeeQuote quote, int buyerPlots,
                         @NotNull List<RealtyBackend.FeeListing> agentFeesIfRun) {

    /** The role an agent's fee is set under. */
    private static final String ROLE_AGENT = "agent";

    public FeePreview {
        agentFeesIfRun = List.copyOf(agentFeesIfRun);
    }

    public FeePreview(@NotNull FeeQuote quote, int buyerPlots) {
        this(quote, buyerPlots, List.of());
    }

    /** Looks up the quote and the plot count together, on the database executor. */
    public static @NotNull CompletableFuture<FeePreview> of(@NotNull RealtyPaperApi api,
                                                            @NotNull WorldGuardRegion region,
                                                            @NotNull UUID buyerId,
                                                            double price) {
        return api.previewFees(region, buyerId, price)
                .thenCombine(api.listOwnedRegions(Party.personal(buyerId), 1, 0),
                        (quote, owned) -> new FeePreview(quote, owned.totalCount()));
    }

    /**
     * As {@link #of}, for an offer: whichever agent accepts it adds their own fee, so each approved
     * agent fee is listed as one that applies if that agent accepts. A failed lookup of the fees
     * only leaves those lines out.
     */
    public static @NotNull CompletableFuture<FeePreview> ofOffer(@NotNull RealtyPaperApi api,
                                                                 @NotNull WorldGuardRegion region,
                                                                 @NotNull UUID buyerId,
                                                                 double price) {
        CompletableFuture<List<RealtyBackend.FeeListing>> agentFees = api.listFees(region)
                .thenApply(FeePreview::approvedAgentFees)
                .exceptionally(ex -> List.of());
        return of(api, region, buyerId, price).thenCombine(agentFees,
                (preview, agents) -> new FeePreview(preview.quote(), preview.buyerPlots(), agents));
    }

    /** The approved fees set under the agent role: each applies only to a sale its agent runs. */
    static @NotNull List<RealtyBackend.FeeListing> approvedAgentFees(@NotNull List<RealtyBackend.FeeListing> fees) {
        return fees.stream()
                .filter(fee -> ROLE_AGENT.equals(fee.role()) && fee.approved() && fee.recipient() != null)
                .toList();
    }

    public boolean hasFees() {
        return !quote.lines().isEmpty();
    }

    /**
     * {@code message}, followed by {@code headerKey}, the breakdown and the agent fees that apply
     * only if their agent runs the sale; {@code message} alone when there are none of either.
     */
    public @NotNull Component appendTo(@NotNull Component message,
                                       @NotNull String headerKey,
                                       @NotNull MessageContainer messages,
                                       @NotNull PartyNames names) {
        if (!hasFees() && agentFeesIfRun.isEmpty()) {
            return message;
        }
        Component result = message.appendNewline().append(messages.messageFor(headerKey));
        if (hasFees()) {
            result = result.appendNewline()
                    .append(FeeQuoteFormatter.breakdown(quote, buyerPlots, messages, names::display));
        }
        for (RealtyBackend.FeeListing agentFee : agentFeesIfRun) {
            result = result.appendNewline().append(messages.messageFor(MessageKeys.FEES_BREAKDOWN_AGENT_IF_ACCEPTS,
                    Placeholder.unparsed("agent", names.display(agentFee.recipient())),
                    Placeholder.unparsed("side", agentFee.side().name().toLowerCase(Locale.ROOT)),
                    Placeholder.unparsed("percent", agentFee.rate())));
        }
        return result;
    }
}
