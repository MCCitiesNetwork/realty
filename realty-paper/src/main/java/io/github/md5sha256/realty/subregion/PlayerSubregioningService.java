package io.github.md5sha256.realty.subregion;

import io.github.md5sha256.realty.command.util.WorldGuardRegion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSubregioningService {

    private final Map<UUID, PlayerSubregioningState> states = new ConcurrentHashMap<>();

    public @NotNull PlayerSubregioningState startSubregioning(@NotNull Player player,
                                                              @NotNull WorldGuardRegion parentRegion) {
        PlayerSubregioningState state = new PlayerSubregioningState(player, parentRegion);
        this.states.put(player.getUniqueId(), state);
        return state;
    }

    public @Nullable PlayerSubregioningState getState(@NotNull UUID playerId) {
        return this.states.get(playerId);
    }

    public @Nullable PlayerSubregioningState removeState(@NotNull UUID playerId) {
        return this.states.remove(playerId);
    }

}
