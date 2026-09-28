package io.github.md5sha256.realty.schematic;

import io.github.md5sha256.realty.api.RealtySchematicFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

class RealtySchematicEncoderTest {

    /** The fixture from the plan. The explorer's tests decode the same bytes. */
    private static final String GOLDEN =
            "UkxUWQF4nFWNQQrCMBBFJ426E72G0BMIOYkUGcOkCbaJZGbTA/ceHUWILv5iHu//ATivAGA13Tc7OM4pk68Y5IqpKjo1"
                    + "wFIyKXINFXzeWdTkW0Cf8uhyqRL7iFNwjyJS5p4jvsixVExjlEEHLm3AR2L578ry1vWeaPh9/1G1vTcGbGfgYM0G"
                    + "3807YQ==";

    private static final List<BlockGrid.PaletteEntry> PALETTE = List.of(
            BlockGrid.PaletteEntry.AIR,
            new BlockGrid.PaletteEntry("minecraft:stone", "", BlockGrid.Sight.SOLID),
            new BlockGrid.PaletteEntry(
                    "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", "",
                    BlockGrid.Sight.CLEAR),
            new BlockGrid.PaletteEntry(
                    "minecraft:chest[facing=north,type=single]", "minecraft:chest",
                    BlockGrid.Sight.CLEAR));

    private static BlockGrid sample() {
        BlockGrid grid = new BlockGrid(3, 2, 2, PALETTE, new int[12]);
        grid.cells()[grid.index(0, 0, 0)] = 1;
        grid.cells()[grid.index(1, 0, 0)] = 2;
        grid.cells()[grid.index(2, 1, 1)] = 3;
        return grid;
    }

    @Test
    void opensWithTheRealtyHeader() throws Exception {
        byte[] bytes = RealtySchematicEncoder.encode(sample(), 4325);

        Assertions.assertTrue(RealtySchematicFormat.isReadable(bytes));
        Assertions.assertArrayEquals(RealtySchematicFormat.header(),
                Arrays.copyOf(bytes, RealtySchematicFormat.HEADER_LENGTH));
    }

    @Test
    void isNotSomethingWorldEditWouldOpen() throws Exception {
        byte[] bytes = RealtySchematicEncoder.encode(sample(), 4325);

        // WorldEdit's readers all begin by un-gzipping.
        Assertions.assertFalse(bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b);
    }

    @Test
    void whatIsWrittenReadsBackTheSame() throws Exception {
        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(sample(), 4325));

        Assertions.assertEquals(4325, decoded.dataVersion());
        Assertions.assertEquals(3, decoded.width());
        Assertions.assertEquals(2, decoded.height());
        Assertions.assertEquals(2, decoded.length());
        Assertions.assertEquals(PALETTE.stream().map(BlockGrid.PaletteEntry::state).toList(),
                decoded.states());
        Assertions.assertEquals(List.of("", "", "", "minecraft:chest"), decoded.blockEntityIds());
        Assertions.assertArrayEquals(new int[]{1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 3}, decoded.cells());
    }

    @Test
    void theGoldenFixtureStillDecodes() throws Exception {
        // If this fails the layout has changed, and every capture already stored is
        // unreadable. Change the version byte; do not change the fixture.
        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(Base64.getDecoder().decode(GOLDEN));

        Assertions.assertEquals(4325, decoded.dataVersion());
        Assertions.assertEquals(List.of("", "", "", "minecraft:chest"), decoded.blockEntityIds());
        Assertions.assertArrayEquals(new int[]{1, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 3}, decoded.cells());
    }

    @Test
    void neighbouringCellsOfOneBlockShareARun() throws Exception {
        BlockGrid air = new BlockGrid(10, 10, 10, List.of(BlockGrid.PaletteEntry.AIR), new int[1000]);

        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(air, 4325));

        Assertions.assertEquals(1, decoded.runCount());
        Assertions.assertArrayEquals(new int[1000], decoded.cells());
    }

    @Test
    void aRunLongerThanOneVarintByteSurvives() throws Exception {
        int[] cells = new int[300];
        Arrays.fill(cells, 0, 200, 1);
        BlockGrid grid = new BlockGrid(300, 1, 1, PALETTE, cells);

        RealtySchematicTestDecoder.Decoded decoded =
                RealtySchematicTestDecoder.decode(RealtySchematicEncoder.encode(grid, 4325));

        Assertions.assertEquals(2, decoded.runCount());
        Assertions.assertArrayEquals(cells, decoded.cells());
    }

    @Test
    void whatABlockDoesToTheViewIsNotWritten() throws Exception {
        List<BlockGrid.PaletteEntry> flipped = List.of(
                BlockGrid.PaletteEntry.AIR,
                new BlockGrid.PaletteEntry("minecraft:stone", "", BlockGrid.Sight.CLEAR));
        List<BlockGrid.PaletteEntry> original = List.of(
                BlockGrid.PaletteEntry.AIR,
                new BlockGrid.PaletteEntry("minecraft:stone", "", BlockGrid.Sight.SOLID));

        Assertions.assertArrayEquals(
                RealtySchematicEncoder.encode(new BlockGrid(1, 1, 1, original, new int[]{1}), 1),
                RealtySchematicEncoder.encode(new BlockGrid(1, 1, 1, flipped, new int[]{1}), 1));
    }
}
