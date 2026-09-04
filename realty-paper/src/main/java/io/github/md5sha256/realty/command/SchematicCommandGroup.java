package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import io.github.md5sha256.realty.api.DurationFormatter;
import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.schematic.CaptureCooldown;
import io.github.md5sha256.realty.schematic.CaptureRegistry;
import io.github.md5sha256.realty.schematic.RegionSchematicWriter;
import io.github.md5sha256.realty.schematic.RegionVolume;
import io.github.md5sha256.realty.schematic.TickScheduler;
import io.github.md5sha256.realty.schematic.TickSlicedCopy;
import io.github.md5sha256.realty.settings.Settings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Groups the schematic subcommands under {@code /realty schematic}.
 *
 * <ul>
 *   <li>{@code /realty schematic capture [region] [--force]} -- snapshot a region's blocks</li>
 * </ul>
 *
 * <p>The capture is spread across ticks rather than run to completion in one, so a
 * large region never stalls the server. It is not asynchronous: Paper forbids chunk
 * access off the main thread, so only the encode and the database write -- neither of
 * which touches the world -- leave it.</p>
 */
public record SchematicCommandGroup(
        @NotNull RealtyBackend backend,
        @NotNull ExecutorState executors,
        @NotNull TickScheduler scheduler,
        @NotNull CaptureCooldown cooldown,
        @NotNull CaptureRegistry registry,
        @NotNull AtomicReference<Settings> settings,
        @NotNull MessageContainer messages,
        @NotNull Logger logger
) implements CustomCommandBean {

    private static final CommandFlag<Void> FORCE_FLAG =
            CommandFlag.<Source>builder("force")
                    .withDescription(Description.of("Bypass the per-region capture cooldown"))
                    .build();

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        return List.of(
                builder.literal("schematic")
                        .literal("capture")
                        .permission("realty.command.schematic.capture")
                        .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                        .flag(FORCE_FLAG)
                        .handler(this::executeCapture)
                        .build()
        );
    }

    private void executeCapture(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }

        boolean force = ctx.flags().isPresent(FORCE_FLAG.name());

        // Checked before the cooldown, so an unauthorised --force is refused outright
        // rather than silently ignored. Console is trusted, as elsewhere.
        if (force && sender instanceof Player player
                && !player.hasPermission("realty.command.schematic.capture.force")) {
            sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_FORCE_NO_PERMISSION));
            return;
        }

        String regionId = region.region().getId();
        UUID worldId = region.world().getUID();
        Settings current = this.settings.get();

        World weWorld = BukkitAdapter.adapt(region.world());
        Region weRegion = new CuboidRegion(weWorld,
                region.region().getMinimumPoint(), region.region().getMaximumPoint());

        // The cap is hard: --force does not lift it. It guards the database write, and
        // a limit waivable by whoever hits it guards nothing.
        long volume = RegionVolume.of(weRegion);
        if (RegionVolume.exceedsCap(weRegion, current.schematicMaxVolume())) {
            sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_TOO_LARGE,
                    Placeholder.unparsed("region", regionId),
                    Placeholder.unparsed("volume", Long.toString(volume)),
                    Placeholder.unparsed("cap", Long.toString(current.schematicMaxVolume()))));
            return;
        }

        if (!force) {
            Duration remaining = this.cooldown.remaining(regionId, worldId,
                    Duration.ofSeconds(current.schematicCaptureCooldownSeconds()));
            if (remaining != null) {
                sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_COOLDOWN,
                        Placeholder.unparsed("region", regionId),
                        Placeholder.unparsed("remaining", DurationFormatter.format(remaining))));
                return;
            }
        }

        TickSlicedCopy copy = TickSlicedCopy.start(weWorld, weRegion,
                current.schematicCaptureBlocksPerTick(), this.scheduler,
                clipboard -> persist(sender, regionId, worldId, clipboard),
                reason -> {
                    this.registry.finish(regionId, worldId);
                    sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_ABORTED,
                            Placeholder.unparsed("region", regionId),
                            Placeholder.unparsed("reason", reason)));
                });

        // The cooldown usually prevents overlap, but --force bypasses it, so a second
        // capture has to be refused separately.
        if (!this.registry.begin(regionId, worldId, copy)) {
            copy.cancel();
            sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_ALREADY_RUNNING,
                    Placeholder.unparsed("region", regionId)));
            return;
        }

        sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_STARTED,
                Placeholder.unparsed("region", regionId),
                Placeholder.unparsed("volume", Long.toString(volume))));
    }

    /**
     * Encodes and stores off the main thread -- neither step touches the world -- then
     * returns to the main thread to report.
     */
    private void persist(@NotNull CommandSender sender,
                         @NotNull String regionId,
                         @NotNull UUID worldId,
                         @NotNull Clipboard clipboard) {
        this.executors.dbExec().execute(() -> {
            try {
                byte[] bytes = RegionSchematicWriter.writeClipboard(clipboard);
                boolean stored = this.backend.storeSchematic(regionId, worldId, bytes);
                this.executors.mainThreadExec().execute(() -> {
                    if (stored) {
                        this.cooldown.record(regionId, worldId);
                        sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_CAPTURED,
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("size", Integer.toString(bytes.length))));
                    } else {
                        sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_NOT_REGISTERED,
                                Placeholder.unparsed("region", regionId)));
                    }
                });
            } catch (IOException | RuntimeException e) {
                this.logger.log(Level.WARNING, "Failed to capture schematic for " + regionId, e);
                this.executors.mainThreadExec().execute(() ->
                        sender.sendMessage(messages.messageFor(MessageKeys.SCHEMATIC_FAILED,
                                Placeholder.unparsed("region", regionId),
                                Placeholder.unparsed("error", String.valueOf(e.getMessage())))));
            } finally {
                // In a finally so a thrown capture cannot wedge the region as
                // permanently in-flight.
                this.registry.finish(regionId, worldId);
            }
        });
    }
}
