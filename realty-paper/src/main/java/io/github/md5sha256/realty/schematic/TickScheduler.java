package io.github.md5sha256.realty.schematic;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * The one thing {@link TickSlicedCopy} needs from Bukkit's scheduler, narrowed to a
 * single method so the copy can be tested by driving ticks by hand.
 */
public interface TickScheduler {

    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }

    /**
     * Runs {@code task} once per tick until the returned handle is cancelled.
     */
    @NotNull Cancellable repeating(@NotNull Runnable task);

    /**
     * Runs on the server's main thread, which is where world reads must happen.
     */
    record Bukkit(@NotNull Plugin plugin) implements TickScheduler {

        @Override
        public @NotNull Cancellable repeating(@NotNull Runnable task) {
            // Qualified: this record shadows org.bukkit.Bukkit inside its own body.
            BukkitTask handle = org.bukkit.Bukkit.getScheduler()
                    .runTaskTimer(this.plugin, task, 1L, 1L);
            return handle::cancel;
        }
    }
}
