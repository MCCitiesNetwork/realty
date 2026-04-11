package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.ExecutorState;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import io.github.md5sha256.realty.settings.ConfigRegionTag;
import io.github.md5sha256.realty.settings.RegionTagSettings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;



public record CleanupCommandGroup(
        @NotNull Database database,
        @NotNull ExecutorState executorState,
        @NotNull AtomicReference<RegionTagSettings> regionTagSettings,
        @NotNull MessageContainer messages
) implements CustomCommandBean {

    @Override
    public @NotNull List<Command<? extends Source>> commands(@NotNull Command.Builder<Source> builder) {
        Command<? extends Source> cleanupTags = builder
                .literal("cleanup")
                .literal("tags")
                .permission("realty.command.cleanup.tags")
                .handler(this::executeCleanupTags)
                .build();
        return List.of(cleanupTags);
    }

    private void executeCleanupTags(@NotNull CommandContext<Source> ctx) {
        CommandSender sender = ctx.sender().source();
        Set<String> configTagIds = regionTagSettings.get().tags().stream()
                .map(ConfigRegionTag::tagId)
                .collect(Collectors.toSet());
        CompletableFuture.runAsync(() -> {
            try (SqlSessionWrapper session = database.openSession(true)) {
                RegionTagMapper mapper = session.regionTagMapper();
                int deleted;
                if (configTagIds.isEmpty()) {
                    deleted = mapper.deleteAll();
                } else {
                    deleted = mapper.deleteByTagIdNotIn(configTagIds);
                }
                if (deleted == 0) {
                    sender.sendMessage(messages.messageFor(MessageKeys.CLEANUP_TAGS_NONE));
                } else {
                    sender.sendMessage(messages.messageFor(MessageKeys.CLEANUP_TAGS_SUCCESS,
                            Placeholder.unparsed("count", String.valueOf(deleted))));
                }
            } catch (Exception ex) {
                sender.sendMessage(messages.messageFor(MessageKeys.CLEANUP_TAGS_ERROR,
                        Placeholder.unparsed("error", ex.getMessage())));
            }
        }, executorState.dbExec());
    }

}
