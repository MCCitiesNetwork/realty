package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.command.util.AuthorityParser;
import io.github.md5sha256.realty.command.util.DurationParser;
import io.github.md5sha256.realty.command.util.ParseBounds;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.Settings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Handles {@code /realty create leasehold <name> <price> <period> <maxextensions>}
 * and {@code /realty create freehold <name> [--price <price>] [--titleholder <name>] [--authority <name>]}.
 *
 * <p>Creates a new WorldGuard region from the player's WorldEdit selection, then registers it in Realty.</p>
 *
 * <p>Permissions: {@code realty.command.create.leasehold} / {@code realty.command.create.freehold}.</p>
 */
public record CreateCommand(@NotNull RealtyPaperApi api,
                             @NotNull AtomicReference<Settings> settings,
                             @NotNull MessageContainer messages) implements CustomCommandBean {

    private static final CloudKey<String> NAME = CloudKey.of("name", String.class);
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");
    private static final CloudKey<Double> PRICE = CloudKey.of("price", Double.class);
    private static final CloudKey<Duration> PERIOD = CloudKey.of("period", Duration.class);
    private static final CloudKey<Integer> MAX_EXTENSIONS = CloudKey.of("maxextensions", Integer.class);
    private static final CommandFlag<UUID> AUTHORITY_FLAG =
            CommandFlag.<Source>builder("authority")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<UUID> TITLEHOLDER_FLAG =
            CommandFlag.<Source>builder("titleholder")
                    .withComponent(AuthorityParser.authority())
                    .build();

    private static final CommandFlag<Double> PRICE_FLAG =
            CommandFlag.<Source>builder("price")
                    .withComponent(DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                            Double.MAX_VALUE))
                    .build();

    private static final CommandFlag<UUID> LANDLORD_FLAG =
            CommandFlag.<Source>builder("landlord")
                    .withComponent(AuthorityParser.authority())
                    .build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        var base = builder
                .literal("create");
        return List.of(
                base.literal("leasehold")
                        .permission("realty.command.create.leasehold")
                        .required(NAME, StringParser.stringParser())
                        .required(PRICE, DoubleParser.doubleParser(ParseBounds.MIN_STRICTLY_POSITIVE,
                                Double.MAX_VALUE))
                        .required(PERIOD, DurationParser.duration())
                        .required(MAX_EXTENSIONS, IntegerParser.integerParser(-1))
                        .flag(LANDLORD_FLAG)
                        .handler(this::executeLeasehold)
                        .build(),
                base.literal("freehold")
                        .permission("realty.command.create.freehold")
                        .required(NAME, StringParser.stringParser())
                        .flag(PRICE_FLAG)
                        .flag(TITLEHOLDER_FLAG)
                        .flag(AUTHORITY_FLAG)
                        .handler(this::executeFreehold)
                        .build()
        );
    }

    private void executeLeasehold(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        String name = ctx.get(NAME);
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            player.sendMessage(messages.messageFor(MessageKeys.CREATE_INVALID_NAME,
                    Placeholder.unparsed("region", name)));
            return;
        }
        double price = ctx.get(PRICE);
        Duration period = ctx.get(PERIOD);
        int maxExtensions = ctx.get(MAX_EXTENSIONS);
        UUID landlord = ctx.flags()
                .getValue(LANDLORD_FLAG, settings.get().defaultLeaseholdAuthority());

        RegionManager regionManager = getRegionManager(player.getWorld());
        if (regionManager == null) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Region manager unavailable")));
            return;
        }
        if (regionManager.getRegion(name) != null) {
            player.sendMessage(messages.messageFor(MessageKeys.CREATE_REGION_EXISTS,
                    Placeholder.unparsed("region", name)));
            return;
        }
        Region selection = getSelection(player);
        if (selection == null) {
            player.sendMessage(messages.messageFor(MessageKeys.CREATE_INCOMPLETE_SELECTION));
            return;
        }

        ProtectedRegion wgRegion = createProtectedRegion(name, selection);
        regionManager.addRegion(wgRegion);
        World world = player.getWorld();
        WorldGuardRegion region = new WorldGuardRegion(wgRegion, world);

        api.createLeasehold(region, price, period.toSeconds(), maxExtensions, landlord)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.CreateLeaseholdResult.Success ignored ->
                                player.sendMessage(messages.messageFor(MessageKeys.CREATE_LEASEHOLD_SUCCESS,
                                        Placeholder.unparsed("region", name)));
                        case RealtyPaperApi.CreateLeaseholdResult.AlreadyRegistered ignored -> {
                            regionManager.removeRegion(name);
                            player.sendMessage(messages.messageFor(MessageKeys.CREATE_ALREADY_REGISTERED,
                                    Placeholder.unparsed("region", name)));
                        }
                        case RealtyPaperApi.CreateLeaseholdResult.Error error ->
                                player.sendMessage(messages.messageFor(MessageKeys.CREATE_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    player.sendMessage(messages.messageFor(MessageKeys.CREATE_ERROR,
                            Placeholder.unparsed("error", cause.getMessage())));
                    return null;
                });
    }

    private void executeFreehold(@NotNull CommandContext<Source> ctx) {
        if (!(ctx.sender().source() instanceof Player player)) {
            ctx.sender().source().sendMessage(messages.messageFor(MessageKeys.COMMON_PLAYERS_ONLY));
            return;
        }
        String name = ctx.get(NAME);
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            player.sendMessage(messages.messageFor(MessageKeys.CREATE_INVALID_NAME,
                    Placeholder.unparsed("region", name)));
            return;
        }
        Double price = ctx.flags().getValue(PRICE_FLAG, null);
        UUID authority = ctx.flags()
                .getValue(AUTHORITY_FLAG, settings.get().defaultFreeholdAuthority());
        UUID titleholder = ctx.flags()
                .getValue(TITLEHOLDER_FLAG, settings.get().defaultFreeholdTitleholder());

        RegionManager regionManager = getRegionManager(player.getWorld());
        if (regionManager == null) {
            player.sendMessage(messages.messageFor(MessageKeys.COMMON_ERROR,
                    Placeholder.unparsed("error", "Region manager unavailable")));
            return;
        }
        if (regionManager.getRegion(name) != null) {
            player.sendMessage(messages.messageFor(MessageKeys.CREATE_REGION_EXISTS,
                    Placeholder.unparsed("region", name)));
            return;
        }
        Region selection = getSelection(player);
        if (selection == null) {
            player.sendMessage(messages.messageFor(MessageKeys.CREATE_INCOMPLETE_SELECTION));
            return;
        }

        ProtectedRegion wgRegion = createProtectedRegion(name, selection);
        regionManager.addRegion(wgRegion);
        World world = player.getWorld();
        WorldGuardRegion region = new WorldGuardRegion(wgRegion, world);

        api.createFreehold(region, price, authority, titleholder)
                .thenAccept(result -> {
                    switch (result) {
                        case RealtyPaperApi.CreateFreeholdResult.Success ignored ->
                                player.sendMessage(messages.messageFor(MessageKeys.CREATE_FREEHOLD_SUCCESS,
                                        Placeholder.unparsed("region", name)));
                        case RealtyPaperApi.CreateFreeholdResult.AlreadyRegistered ignored -> {
                            regionManager.removeRegion(name);
                            player.sendMessage(messages.messageFor(MessageKeys.CREATE_ALREADY_REGISTERED,
                                    Placeholder.unparsed("region", name)));
                        }
                        case RealtyPaperApi.CreateFreeholdResult.Error error ->
                                player.sendMessage(messages.messageFor(MessageKeys.CREATE_ERROR,
                                        Placeholder.unparsed("error", error.message())));
                    }
                }).exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace();
                    player.sendMessage(messages.messageFor(MessageKeys.CREATE_ERROR,
                            Placeholder.unparsed("error", cause.getMessage())));
                    return null;
                });
    }

    private static RegionManager getRegionManager(@NotNull World world) {
        RegionContainer regionContainer = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer();
        return regionContainer.get(BukkitAdapter.adapt(world));
    }

    private static Region getSelection(@NotNull Player player) {
        SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
        LocalSession localSession = sessionManager.get(BukkitAdapter.adapt(player));
        if (!Objects.equals(localSession.getSelectionWorld(), BukkitAdapter.adapt(player.getWorld()))) {
            return null;
        }
        try {
            return localSession.getSelection().clone();
        } catch (IncompleteRegionException ex) {
            return null;
        }
    }

    private static @NotNull ProtectedRegion createProtectedRegion(@NotNull String name,
                                                                    @NotNull Region selection) {
        if (selection instanceof CuboidRegion cuboid) {
            return new ProtectedCuboidRegion(name,
                    cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
        } else if (selection instanceof Polygonal2DRegion polygon) {
            return new ProtectedPolygonalRegion(name,
                    polygon.getPoints(), polygon.getMinimumY(), polygon.getMaximumY());
        }
        return new ProtectedCuboidRegion(name,
                selection.getMinimumPoint(), selection.getMaximumPoint());
    }

}
