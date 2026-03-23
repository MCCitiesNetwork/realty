package io.github.md5sha256.realty.command.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Lazily iterates block positions using an expanding-cube search with per-face pruning.
 *
 * <p>Six faces (North, East, South, West, Top, Bottom) are iterated at each radius.
 * A face is deactivated for subsequent radii if it contained no non-empty blocks
 * (as determined by the supplied {@code nonEmptyPredicate}). Iteration stops when the
 * position budget is exhausted or all faces are inactive.</p>
 *
 * <p>Positions outside the valid Y range are consumed from the budget but never yielded.</p>
 */
public final class ExpandingCubeBlockIterator implements Iterator<BlockPosition> {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int maxPositions;
    private final int minY;
    private final int maxY;
    private final Predicate<BlockPosition> nonEmptyPredicate;

    private final boolean[] faceActive = {true, true, true, true, true, true};
    private int radius = 1;
    private int face = 0;
    private int outerIdx;
    private int innerIdx;
    private int outerEnd;
    private int outerStep;
    private int innerStart;
    private int innerEnd;
    private int checked = 0;
    private boolean faceInitialized = false;
    private boolean currentFaceNonEmpty;
    private boolean anyActiveInRadius;

    private BlockPosition next;
    private boolean exhausted;

    /**
     * @param centerX           center X coordinate
     * @param centerY           center Y coordinate
     * @param centerZ           center Z coordinate
     * @param maxPositions      maximum number of positions to consider (budget)
     * @param minY              minimum valid Y (world min height)
     * @param maxY              maximum valid Y (world max height)
     * @param nonEmptyPredicate returns {@code true} if the block at the position is non-empty;
     *                          used for face-pruning decisions
     */
    public ExpandingCubeBlockIterator(int centerX, int centerY, int centerZ,
                                      int maxPositions, int minY, int maxY,
                                      @NotNull Predicate<BlockPosition> nonEmptyPredicate) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.maxPositions = maxPositions;
        this.minY = minY;
        this.maxY = maxY;
        this.nonEmptyPredicate = nonEmptyPredicate;
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

    private @Nullable BlockPosition computeNext() {
        while (checked < maxPositions) {
            // Advance to the next active face if the current one isn't initialized
            if (!faceInitialized) {
                if (!advanceToNextFace()) {
                    break;
                }
            }

            // Compute position for the current face, outer, inner
            BlockPosition pos = facePosition();
            checked++;

            // Advance inner/outer indices
            boolean faceComplete = !advanceIndices();

            if (faceComplete) {
                completeFace();
            }

            // Height-invalid positions consume budget but are not yielded
            if (pos.y() < minY || pos.y() + 2 > maxY) {
                continue;
            }

            // Track non-empty for face pruning
            if (nonEmptyPredicate.test(pos)) {
                currentFaceNonEmpty = true;
            }

            return pos;
        }
        exhausted = true;
        return null;
    }

    /**
     * Skips inactive faces and advances to the next radius when all faces for the
     * current radius are done. Returns {@code false} if iteration is exhausted.
     */
    private boolean advanceToNextFace() {
        while (true) {
            // Skip inactive faces
            while (face < 6 && !faceActive[face]) {
                face++;
            }
            if (face < 6) {
                initFace();
                return true;
            }
            // All faces for this radius are done
            if (!anyActiveInRadius) {
                exhausted = true;
                return false;
            }
            radius++;
            face = 0;
            anyActiveInRadius = false;
        }
    }

    private void completeFace() {
        faceActive[face] = currentFaceNonEmpty;
        if (currentFaceNonEmpty) {
            anyActiveInRadius = true;
        }
        face++;
        faceInitialized = false;
    }

    private void initFace() {
        currentFaceNonEmpty = false;
        switch (face) {
            case 0 -> {
                outerIdx = -radius + 1;
                outerEnd = radius;
                outerStep = 1;
                innerStart = -radius + 1;
                innerEnd = radius - 1;
            }
            case 1 -> {
                outerIdx = -radius + 1;
                outerEnd = radius;
                outerStep = 1;
                innerStart = -radius + 1;
                innerEnd = radius - 1;
            }
            case 2 -> {
                outerIdx = radius - 1;
                outerEnd = -radius;
                outerStep = -1;
                innerStart = -radius + 1;
                innerEnd = radius - 1;
            }
            case 3 -> {
                outerIdx = radius - 1;
                outerEnd = -radius;
                outerStep = -1;
                innerStart = -radius + 1;
                innerEnd = radius - 1;
            }
            case 4, 5 -> {
                outerIdx = -radius;
                outerEnd = radius;
                outerStep = 1;
                innerStart = -radius;
                innerEnd = radius;
            }
            default -> throw new IllegalStateException("Invalid face: " + face);
        }
        innerIdx = innerStart;
        faceInitialized = true;
    }

    private BlockPosition facePosition() {
        return switch (face) {
            case 0 -> new BlockPosition(centerX + outerIdx, centerY + innerIdx, centerZ - radius);
            case 1 -> new BlockPosition(centerX + radius, centerY + innerIdx, centerZ + outerIdx);
            case 2 -> new BlockPosition(centerX + outerIdx, centerY + innerIdx, centerZ + radius);
            case 3 -> new BlockPosition(centerX - radius, centerY + innerIdx, centerZ + outerIdx);
            case 4 -> new BlockPosition(centerX + outerIdx, centerY + radius, centerZ + innerIdx);
            case 5 -> new BlockPosition(centerX + outerIdx, centerY - radius, centerZ + innerIdx);
            default -> throw new IllegalStateException("Invalid face: " + face);
        };
    }

    /**
     * Advances inner, then outer indices. Returns {@code false} when the face is complete.
     */
    private boolean advanceIndices() {
        innerIdx++;
        if (innerIdx <= innerEnd) {
            return true;
        }
        innerIdx = innerStart;
        outerIdx += outerStep;
        return outerStep > 0 ? outerIdx <= outerEnd : outerIdx >= outerEnd;
    }
}
