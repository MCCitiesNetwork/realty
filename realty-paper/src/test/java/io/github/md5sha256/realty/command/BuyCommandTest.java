package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyPaperApi;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RegionBoughtEvent;
import org.bukkit.World;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

class BuyCommandTest {

    @Test
    void buySuccess_eventCarriesFees() {
        WorldGuardRegion region = new WorldGuardRegion(
                new ProtectedCuboidRegion("plot", BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10)),
                Mockito.mock(World.class));
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        FeeQuote quote = new FeeQuote(1000,
                List.of(new FeeLine("agent", Party.personal(UUID.randomUUID()), FeeSide.SELLER, 3, 30)),
                1000, 970);

        RegionBoughtEvent event = BuyCommand.boughtEvent(region, buyer,
                new RealtyPaperApi.BuyResult.Success(1000, "plot", Party.personal(seller), quote));

        Assertions.assertEquals(quote, event.getFees());
        Assertions.assertEquals(Party.personal(seller), event.getPreviousTitleHolder());
        Assertions.assertEquals(1000, event.getPrice(), 0.0001);
    }
}
