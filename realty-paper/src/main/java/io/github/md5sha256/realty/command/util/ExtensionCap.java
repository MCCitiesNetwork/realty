package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How many extensions a player may give a lease. A player holding {@link #UNLIMITED_NODE} may give
 * any number, or unlimited; otherwise the highest {@code realty.leasehold.extensions.<n>} they hold
 * is their limit, and a player with none may give 0.
 */
public sealed interface ExtensionCap {

    String UNLIMITED_NODE = "realty.leasehold.extensions.unlimited";
    String NODE_PREFIX = "realty.leasehold.extensions.";

    /** No limit, including unlimited extensions. */
    record Unlimited() implements ExtensionCap {

        @Override
        public boolean allows(int maxExtensions) {
            return true;
        }

        @Override
        public @Nullable Integer asLimit() {
            return null;
        }

        @Override
        public @NotNull Component refusal(@NotNull MessageContainer messages, int requested) {
            throw new IllegalStateException("an unlimited cap refuses nothing");
        }
    }

    /** At most {@code max} extensions, and never unlimited. */
    record AtMost(int max) implements ExtensionCap {

        @Override
        public boolean allows(int maxExtensions) {
            return maxExtensions >= 0 && maxExtensions <= this.max;
        }

        @Override
        public @NotNull Integer asLimit() {
            return this.max;
        }

        @Override
        public @NotNull Component refusal(@NotNull MessageContainer messages, int requested) {
            String key = requested < 0
                    ? MessageKeys.LEASEHOLD_EXTENSIONS_UNLIMITED_NOT_ALLOWED
                    : MessageKeys.LEASEHOLD_EXTENSIONS_ABOVE_CAP;
            return messages.messageFor(key, Placeholder.unparsed("cap", String.valueOf(this.max)));
        }
    }

    /**
     * Reads the cap from the sender's permissions. A sender that is not a player, such as the
     * console, is {@link Unlimited}.
     */
    static @NotNull ExtensionCap of(@NotNull CommandSender sender) {
        // hasPermission, not the effective list, so a permission plugin's wildcards count.
        if (!(sender instanceof Player) || sender.hasPermission(UNLIMITED_NODE)) {
            return new Unlimited();
        }
        int max = 0;
        for (PermissionAttachmentInfo info : sender.getEffectivePermissions()) {
            if (!info.getValue() || !info.getPermission().startsWith(NODE_PREFIX)) {
                continue;
            }
            Integer count = parseCount(info.getPermission().substring(NODE_PREFIX.length()));
            if (count != null && count > max) {
                max = count;
            }
        }
        return new AtMost(max);
    }

    /** Whether {@code maxExtensions} (-1 meaning unlimited) is within the cap. */
    boolean allows(int maxExtensions);

    /** The cap as a proposal accept takes it: {@code null} for no cap. */
    @Nullable Integer asLimit();

    /** The message refusing {@code requested}. Only for a value {@link #allows} refuses. */
    @NotNull Component refusal(@NotNull MessageContainer messages, int requested);

    private static @Nullable Integer parseCount(@NotNull String suffix) {
        if (suffix.isEmpty() || !suffix.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
