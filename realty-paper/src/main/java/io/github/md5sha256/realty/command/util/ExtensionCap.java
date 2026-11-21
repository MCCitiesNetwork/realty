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

import java.util.Optional;
import java.util.OptionalInt;

/**
 * How many extensions a player may give a lease. A player holding {@link #UNLIMITED_NODE} may give
 * any number, or unlimited; otherwise the highest {@code realty.leasehold.extensions.<n>} they hold
 * is their limit, and a player with none may give 0.
 */
public sealed interface ExtensionCap {

    String NODE_PREFIX = "realty.leasehold.extensions.";
    String UNLIMITED_NODE = NODE_PREFIX + "unlimited";

    /** No limit, including unlimited extensions. */
    record Unlimited() implements ExtensionCap {

        @Override
        public @NotNull Optional<Component> refusal(@NotNull MessageContainer messages, int requested) {
            return Optional.empty();
        }
    }

    /** At most {@code max} extensions, and never unlimited. */
    record AtMost(int max) implements ExtensionCap {

        @Override
        public @NotNull Optional<Component> refusal(@NotNull MessageContainer messages, int requested) {
            if (requested >= 0 && requested <= this.max) {
                return Optional.empty();
            }
            String key = requested < 0
                    ? MessageKeys.LEASEHOLD_EXTENSIONS_UNLIMITED_NOT_ALLOWED
                    : MessageKeys.LEASEHOLD_EXTENSIONS_ABOVE_CAP;
            return Optional.of(messages.messageFor(key, Placeholder.unparsed("cap", String.valueOf(this.max))));
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
            max = Math.max(max, parseCount(info.getPermission().substring(NODE_PREFIX.length())).orElse(0));
        }
        return new AtMost(max);
    }

    /**
     * The message refusing {@code requested} extensions (-1 meaning unlimited) on a new lease, or
     * empty when the cap allows it.
     */
    @NotNull Optional<Component> refusal(@NotNull MessageContainer messages, int requested);

    /**
     * As {@link #refusal}, for a lease whose limit is now {@code current} ({@code null} meaning
     * unlimited). A change that does not raise the limit grants nothing, so it is never refused: a
     * capped landlord can lower a lease that is above their cap.
     */
    default @NotNull Optional<Component> refusalOfChange(@NotNull MessageContainer messages, int requested,
                                                         @Nullable Integer current) {
        boolean raises = current != null && (requested < 0 || requested > current);
        return raises ? refusal(messages, requested) : Optional.empty();
    }

    /** The count a node's suffix names; empty when it is not a count. */
    private static @NotNull OptionalInt parseCount(@NotNull String suffix) {
        if (suffix.isEmpty() || !suffix.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(suffix));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }
}
