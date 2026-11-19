package io.github.md5sha256.realty.api;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public record WorldGuardRegion(@NotNull ProtectedRegion region, @NotNull World world) {

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9-]+$");

    /**
     * @return whether Realty accepts {@code name} for a region it creates or renames: letters,
     *         digits and hyphens only
     */
    public static boolean isValidName(@NotNull String name) {
        return VALID_NAME.matcher(name).matches();
    }
}
