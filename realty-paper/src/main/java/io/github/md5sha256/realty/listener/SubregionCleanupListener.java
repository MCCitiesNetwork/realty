package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.subregion.PlayerSubregioningService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class SubregionCleanupListener implements Listener {

    private final PlayerSubregioningService subregioningService;

    public SubregionCleanupListener(@NotNull PlayerSubregioningService subregioningService) {
        this.subregioningService = subregioningService;
    }

    @EventHandler
    private void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        subregioningService.removeState(event.getPlayer().getUniqueId());
    }

}
