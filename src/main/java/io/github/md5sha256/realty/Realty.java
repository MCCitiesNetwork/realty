package io.github.md5sha256.realty;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.md5sha256.realty.command.AcceptOfferCommand;
import io.github.md5sha256.realty.command.AddCommand;
import io.github.md5sha256.realty.command.AuctionCommand;
import io.github.md5sha256.realty.command.BidCommand;
import io.github.md5sha256.realty.command.CancelAuctionCommand;
import io.github.md5sha256.realty.command.CreateRentalCommand;
import io.github.md5sha256.realty.command.CreateSaleCommand;
import io.github.md5sha256.realty.command.CustomCommandBean;
import io.github.md5sha256.realty.command.DeleteCommand;
import io.github.md5sha256.realty.command.InfoCommand;
import io.github.md5sha256.realty.command.ListCommand;
import io.github.md5sha256.realty.command.OfferCommand;
import io.github.md5sha256.realty.command.PayBidCommand;
import io.github.md5sha256.realty.command.PayOfferCommand;
import io.github.md5sha256.realty.command.RemoveCommand;
import io.github.md5sha256.realty.command.WithdrawOfferCommand;
import io.github.md5sha256.realty.database.RealtyLogicImpl;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class Realty extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void registerCommands(
            @NotNull ExecutorState executorState,
            @NotNull RealtyLogicImpl logic,
            @NotNull MessageContainer messageContainer,
            @NotNull Economy economy
    ) {
        List<CustomCommandBean<CommandSourceStack>> commands = List.of(
                new AcceptOfferCommand(executorState, logic, messageContainer),
                new AddCommand(executorState, logic, messageContainer),
                new AuctionCommand(executorState, logic, messageContainer),
                new BidCommand(executorState, logic, messageContainer),
                new CancelAuctionCommand(executorState, logic, messageContainer),
                new CreateRentalCommand(executorState, logic, messageContainer),
                new CreateSaleCommand(executorState, logic, messageContainer),
                new DeleteCommand(executorState, logic, messageContainer),
                new InfoCommand(executorState, logic, messageContainer),
                new ListCommand(executorState, logic, messageContainer),
                new OfferCommand(executorState, logic, messageContainer),
                new PayBidCommand(executorState, logic, economy, messageContainer),
                new PayOfferCommand(executorState, logic, economy, messageContainer),
                new RemoveCommand(executorState, logic, messageContainer),
                new WithdrawOfferCommand(executorState, logic, messageContainer)
        );

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, handler -> {
            var registrar = handler.registrar();
            var root = Commands.literal("realty");
            commands.stream().flatMap(bean -> bean.commands().stream())
                    .map(root::then)
                    .map(LiteralArgumentBuilder::build)
                    .forEach(registrar::register);
        });
    }
}
