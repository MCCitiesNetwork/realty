package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.command.util.WorldGuardRegionParser;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /realty delete <region> [includeworldguard]}.
 *
 * <p>Base permission: {@code realty.command.delete}.
 * Passing the {@code includeworldguard} flag additionally requires
 * {@code realty.command.delete.includeworldguard}.</p>
 */
public record DeleteCommand(
        @NotNull RealtyPaperApi api,
        @NotNull MessageContainer messages
) implements CustomCommandBean.Single {

    @Override
    public @NotNull Command<? extends Source> command(@NotNull Command.Builder<Source> builder) {
        return builder
                .literal("delete")
                .permission("realty.command.delete")
                .required("region", WorldGuardRegionParser.worldGuardRegion())
                .optional("includeworldguard", BooleanParser.booleanParser())
                .handler(this::execute)
                .build();
    }

    private void execute(@NotNull CommandContext<Source> ctx) {
        WorldGuardRegion region = ctx.get("region");
        boolean includeWorldGuard = ctx.getOrDefault("includeworldguard", false);

        CommandSender sender = ctx.sender().source();

        if (includeWorldGuard && !sender.hasPermission("realty.command.delete.includeworldguard")) {
            sender.sendMessage(messages.messageFor(MessageKeys.COMMON_NO_PERMISSION));
            return;
        }

        api.deleteRegion(region, includeWorldGuard).thenAccept(result -> {
            switch (result) {
                case RealtyPaperApi.DeleteResult.Success ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.DELETE_SUCCESS));
                case RealtyPaperApi.DeleteResult.NotRegistered ignored ->
                        sender.sendMessage(messages.messageFor(MessageKeys.DELETE_NOT_REGISTERED));
                case RealtyPaperApi.DeleteResult.WorldGuardSaveError wgError ->
                        sender.sendMessage(messages.messageFor(MessageKeys.DELETE_WORLDGUARD_SAVE_ERROR,
                                Placeholder.unparsed("error", wgError.error())));
                case RealtyPaperApi.DeleteResult.Error error ->
                        sender.sendMessage(messages.messageFor(MessageKeys.DELETE_ERROR,
                                Placeholder.unparsed("error", error.message())));
            }
        });
    }

}
