package io.github.md5sha256.realty.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.md5sha256.realty.command.util.AuthorityArgument;
import io.github.md5sha256.realty.command.util.DurationArgument;
import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionArgument;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.util.ExecutorState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.apache.ibatis.session.SqlSession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles {@code /realty createrental <price> <period> <maxrenewals> <landlord> <region>}.
 *
 * <p>Permission: {@code realty.command.createrental}.</p>
 */
public record CreateRentalCommand(@NotNull ExecutorState executorState,
                                  @NotNull Database database) implements RealtyCommandBean, CustomCommandBean.Single<CommandSourceStack> {

    @Override
    public @NotNull LiteralArgumentBuilder<? extends CommandSourceStack> command() {
        return Commands.literal("createrental")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("realty.command.createrental"))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("period", DurationArgument.duration())
                                .then(Commands.argument("maxrenewals", IntegerArgumentType.integer(-1))
                                        .then(Commands.argument("landlord", new AuthorityArgument())
                                                .then(Commands.argument("region", new WorldGuardRegionArgument())
                                                        .executes(this::execute))))));
    }

    private int execute(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        double price = DoubleArgumentType.getDouble(ctx, "price");
        Duration period = ctx.getArgument("period", Duration.class);
        int maxRenewals = IntegerArgumentType.getInteger(ctx, "maxrenewals");
        UUID landlord = ctx.getArgument("landlord", UUID.class);
        WorldGuardRegion region = ctx.getArgument("region", WorldGuardRegion.class);
        CommandSender sender = ctx.getSource().getSender();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session();) {
                RealtyRegionMapper regionMapper = wrapper.realtyRegionMapper();
                LeaseContractMapper leaseContractMapper = wrapper.leaseContractMapper();

                if (regionMapper.selectByWorldGuardRegion(region.region().getId(), region.world().getUID()) != null) {
                    sender.sendMessage("Region already registered!");
                    return;
                }

                int regionId = regionMapper.registerWorldGuardRegion(region.region().getId(), region.world().getUID());
                leaseContractMapper.insertLease(regionId, price, period, maxRenewals, landlord);
                session.commit();
                sender.sendMessage("Rental region created successfully!");
            }
        }, executorState.dbExec());
        return Command.SINGLE_SUCCESS;
    }

}
