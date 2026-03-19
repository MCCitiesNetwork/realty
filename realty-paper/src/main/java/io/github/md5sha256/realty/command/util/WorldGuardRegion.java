package io.github.md5sha256.realty.command.util;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;


public record WorldGuardRegion(@NotNull ProtectedRegion region, @NotNull World world) {
}
