package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionResolver;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.ConfigRegionTag;
import io.github.md5sha256.realty.settings.RegionTagSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public record TagCommandGroup(
        @NotNull Database database,
        @NotNull ExecutorState executorState,
        @NotNull AtomicReference<RegionTagSettings> regionTagSettings,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        Command<? extends Source> addTag = builder
                .literal("tag")
                .literal("add")
                .permission("realty.command.tag.add")
                .required("tag", StringParser.stringParser(), tagSuggestions())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::executeAdd)
                .build();
        Command<? extends Source> removeTag = builder
                .literal("tag")
                .literal("remove")
                .permission("realty.command.tag.remove")
                .required("tag", StringParser.stringParser(), tagSuggestions())
                .optional("region", WorldGuardRegionResolver.worldGuardRegionResolver())
                .handler(this::executeRemove)
                .build();
        Command<? extends Source> listTags = builder
                .literal("tag")
                .literal("list")
                .permission("realty.command.tag.list")
                .handler(this::executeList)
                .build();
        return List.of(addTag, removeTag, listTags);
    }

    private @NotNull SuggestionProvider<Source> tagSuggestions() {
        return (ctx, input) -> CompletableFuture.completedFuture(
                regionTagSettings.get().tags().stream()
                        .map(ConfigRegionTag::tagId)
                        .map(Suggestion::suggestion)
                        .toList()
        );
    }

    private @Nullable ConfigRegionTag findConfigTag(@NotNull String tagId) {
        for (ConfigRegionTag tag : regionTagSettings.get().tags()) {
            if (tag.tagId().equals(tagId)) {
                return tag;
            }
        }
        return null;
    }

    private void executeAdd(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        String tagId = ctx.get("tag");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        ConfigRegionTag configTag = findConfigTag(tagId);
        if (configTag == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.TAG_UNKNOWN,
                    Placeholder.unparsed("tag", tagId)));
            return;
        }
        if (configTag.permission() != null && !sender.hasPermission(configTag.permission().node())) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_NO_PERMISSION));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                RegionTagMapper mapper = session.regionTagMapper();
                int inserted = mapper.insert(tagId, regionId);
                if (inserted > 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.TAG_ADD_SUCCESS,
                            Placeholder.unparsed("tag", tagId),
                            Placeholder.unparsed("region", regionId)));
                } else {
                    sender.sendMessage(messages.messageFor(MessageKeys.TAG_ADD_FAILED,
                            Placeholder.unparsed("tag", tagId),
                            Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.TAG_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeRemove(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        String tagId = ctx.get("tag");
        WorldGuardRegion region = ctx.<WorldGuardRegion>optional("region")
                .orElseGet(() -> sender instanceof Player player
                        ? WorldGuardRegionResolver.resolveAtLocation(player.getLocation()) : null);
        if (region == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.ERROR_NO_REGION));
            return;
        }
        ConfigRegionTag configTag = findConfigTag(tagId);
        if (configTag == null) {
            sender.sendMessage(messages.messageFor(MessageKeys.TAG_UNKNOWN,
                    Placeholder.unparsed("tag", tagId)));
            return;
        }
        if (configTag.permission() != null && !sender.hasPermission(configTag.permission().node())) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_NO_PERMISSION));
            return;
        }
        String regionId = region.region().getId();
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                RegionTagMapper mapper = session.regionTagMapper();
                int deleted = mapper.deleteByTagAndRegion(tagId, regionId);
                if (deleted > 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.TAG_REMOVE_SUCCESS,
                            Placeholder.unparsed("tag", tagId),
                            Placeholder.unparsed("region", regionId)));
                } else {
                    sender.sendMessage(messages.messageFor(MessageKeys.TAG_REMOVE_NOT_FOUND,
                            Placeholder.unparsed("tag", tagId),
                            Placeholder.unparsed("region", regionId)));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.TAG_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

    private void executeList(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        List<ConfigRegionTag> permitted = regionTagSettings.get().tags().stream()
                .filter(tag -> tag.permission() == null || sender.hasPermission(tag.permission().node()))
                .toList();
        if (permitted.isEmpty()) {
            sender.sendMessage(messages.messageFor(MessageKeys.TAG_LIST_NONE));
            return;
        }
        TextComponent.Builder builder = Component.text();
        builder.append(messages.messageFor(MessageKeys.TAG_LIST_HEADER));
        for (ConfigRegionTag tag : permitted) {
            builder.appendNewline();
            builder.append(messages.messageFor(MessageKeys.TAG_LIST_ENTRY,
                    Placeholder.unparsed("tag", tag.tagId()),
                    Placeholder.component("display", tag.tagDisplayName())));
        }
        sender.sendMessage(builder.build());
    }

}
