package io.github.md5sha256.realty.command.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Lazily iterates block positions on the shells of expanding cubes around a center point.
 *
 * <p>At each radius {@code r} (from 1 to {@code maxRadius}), yields every position
 * where at least one coordinate offset has absolute value equal to {@code r}
 * (i.e. the hollow shell of the cube).</p>
 */
public final class CubeShellBlockIterator implements Iterator<BlockPosition> {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int maxRadius;

    private int radius;
    private int dx;
    private int dy;
    private int dz;
    private BlockPosition next;
    private boolean exhausted;

    public CubeShellBlockIterator(int centerX, int centerY, int centerZ, int maxRadius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.maxRadius = maxRadius;
        this.radius = 1;
        this.dx = -1;
        this.dy = -1;
        this.dz = -1;
    }

    @Override
    public boolean hasNext() {
        if (exhausted) {
            return false;
        }
        if (next != null) {
            return true;
        }
        next = computeNext();
        return next != null;
    }

    @Override
    public BlockPosition next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        BlockPosition result = next;
        next = null;
        return result;
    }

    private BlockPosition computeNext() {
        while (radius <= maxRadius) {
            int currentRadius = radius;
            int currentDx = dx;
            int currentDy = dy;
            int currentDz = dz;

            // Advance state
            dz++;
            if (dz > currentRadius) {
                dz = -currentRadius;
                dy++;
                if (dy > currentRadius) {
                    dy = -currentRadius;
                    dx++;
                    if (dx > currentRadius) {
                        radius++;
                        dx = -radius;
                        dy = -radius;
                        dz = -radius;
                    }
                }
            }

            // Only yield positions on the shell
            if (Math.abs(currentDx) != currentRadius
                    && Math.abs(currentDy) != currentRadius
                    && Math.abs(currentDz) != currentRadius) {
                continue;
            }

            return new BlockPosition(
                    centerX + currentDx,
                    centerY + currentDy,
                    centerZ + currentDz
            );
        }
        exhausted = true;
        return null;
    }
}
