package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.database.entity.InboundOfferView;
import io.github.md5sha256.realty.database.entity.OutboundOfferView;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty offers outbound} and {@code /realty offers inbound}.
 *
 * <p>Permission: {@code realty.command.offers}.</p>
 */
public record OffersCommand(
        @NotNull ExecutorState executorState,
        @NotNull RealtyLogicImpl logic,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public @NotNull List<Command<CommandSourceStack>> commands(@NotNull CommandManager<CommandSourceStack> manager) {
        var base = manager.commandBuilder("realty")
                .literal("offers")
                .permission("realty.command.offers");
        return List.of(
                base.literal("outbound").handler(this::executeOutbound).build(),
                base.literal("inbound").handler(this::executeInbound).build()
        );
    }

    private void executeOutbound(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor("common.players-only"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                List<OutboundOfferView> offers = logic.listOutboundOffers(sender.getUniqueId());

                if (offers.isEmpty()) {
                    sender.sendMessage(messages.messageFor("offers-list.no-offers"));
                    return;
                }

                Component output = messages.messageFor("offers-list.header");

                for (OutboundOfferView offer : offers) {
                    String status;
                    if (offer.accepted()) {
                        double remaining = offer.offerPrice() - offer.currentPayment();
                        status = "Accepted — Paid " + String.format("%.2f", offer.currentPayment())
                                + " / " + String.format("%.2f", offer.offerPrice())
                                + " (remaining: " + String.format("%.2f", remaining) + ")";
                    } else {
                        status = "Pending";
                    }

                    output = output.appendNewline().append(messages.messageFor("offers-list.entry",
                            Placeholder.unparsed("region", offer.worldGuardRegionId()),
                            Placeholder.unparsed("price", String.format("%.2f", offer.offerPrice())),
                            Placeholder.unparsed("date", offer.offerTime().format(DATE_FORMAT)),
                            Placeholder.unparsed("status", status)));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("offers-list.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeInbound(@NotNull CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.sender().getSender() instanceof Player sender)) {
            ctx.sender().getSender().sendMessage(messages.messageFor("common.players-only"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                List<InboundOfferView> offers = logic.listInboundOffers(sender.getUniqueId());

                if (offers.isEmpty()) {
                    sender.sendMessage(messages.messageFor("offers-inbound.no-offers"));
                    return;
                }

                Component output = messages.messageFor("offers-inbound.header");

                for (InboundOfferView offer : offers) {
                    OfflinePlayer offerer = Bukkit.getOfflinePlayer(offer.offererId());
                    String offererName = offerer.getName() != null ? offerer.getName() : offer.offererId().toString();

                    String status;
                    if (offer.accepted()) {
                        double remaining = offer.offerPrice() - offer.currentPayment();
                        status = "Accepted — Paid " + String.format("%.2f", offer.currentPayment())
                                + " / " + String.format("%.2f", offer.offerPrice())
                                + " (remaining: " + String.format("%.2f", remaining) + ")";
                    } else {
                        status = "Pending";
                    }

                    output = output.appendNewline().append(messages.messageFor("offers-inbound.entry",
                            Placeholder.unparsed("region", offer.worldGuardRegionId()),
                            Placeholder.unparsed("player", offererName),
                            Placeholder.unparsed("price", String.format("%.2f", offer.offerPrice())),
                            Placeholder.unparsed("date", offer.offerTime().format(DATE_FORMAT)),
                            Placeholder.unparsed("status", status)));
                }

                sender.sendMessage(output);
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor("offers-inbound.error",
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
