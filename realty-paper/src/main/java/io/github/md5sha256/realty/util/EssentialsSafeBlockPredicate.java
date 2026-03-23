package io.github.md5sha256.realty.util;

import com.earth2me.essentials.utils.LocationUtil;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Safety predicate that delegates to EssentialsX's {@link LocationUtil#isBlockUnsafe}
 * for determining whether a player can safely stand at a given feet-level block.
 */
public final class EssentialsSafeBlockPredicate implements Predicate<Block> {

    @Override
    public boolean test(@NotNull Block feetBlock) {
        return !LocationUtil.isBlockUnsafe(
                feetBlock.getWorld(),
                feetBlock.getX(),
                feetBlock.getY(),
                feetBlock.getZ()
        );
    }
}
