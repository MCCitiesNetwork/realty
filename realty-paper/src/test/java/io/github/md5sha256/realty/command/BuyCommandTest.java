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
import io.github.md5sha256.realty.command.util.FeePreview;
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

    @Test
    void confirmLink_runsTheSameBuyConfirmedAtTheTotalShown() {
        Assertions.assertEquals("/realty buy c404 --confirm 1050.00", BuyCommand.confirmCommand("c404", 1050));
        Assertions.assertEquals("/realty buy c404 --confirm 1050.38", BuyCommand.confirmCommand("c404", 1050.376));
        Assertions.assertEquals("/realty buy c404 --confirm 1234567.50", BuyCommand.confirmCommand("c404", 1234567.5));
    }

    private static FeePreview preview(double price, double total) {
        List<FeeLine> lines = total > price
                ? List.of(new FeeLine("stamp-duty", Party.personal(UUID.randomUUID()), FeeSide.BUYER, 5, total - price))
                : List.of();
        return new FeePreview(new FeeQuote(price, lines, total, price), 0);
    }

    @Test
    void unconfirmed_withFees_isShownFirst_withoutFees_buys() {
        Assertions.assertEquals(BuyCommand.Step.SHOW, BuyCommand.step(null, preview(1000, 1050)));
        Assertions.assertEquals(BuyCommand.Step.BUY, BuyCommand.step(null, preview(1000, 1000)));
    }

    @Test
    void confirmed_atTheSameTotal_buys() {
        Assertions.assertEquals(BuyCommand.Step.BUY, BuyCommand.step(1050.0, preview(1000, 1050)));
        Assertions.assertEquals(BuyCommand.Step.BUY, BuyCommand.step(1050.004, preview(1000, 1050)),
                "compared in cents");
    }

    @Test
    void confirmed_atAnotherTotal_showsTheNewOne_insteadOfBuying() {
        Assertions.assertEquals(BuyCommand.Step.SHOW_CHANGED, BuyCommand.step(1050.0, preview(1000, 1080)));
        Assertions.assertEquals(BuyCommand.Step.SHOW_CHANGED, BuyCommand.step(1050.0, preview(1000, 1000)),
                "the fees went away: still shown, not bought at a total the buyer did not see");
    }

    @Test
    void confirmFlag_takesTheTotal_withOrWithoutARegion() {
        java.util.concurrent.atomic.AtomicReference<org.incendo.cloud.context.CommandContext<Object>> last =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.incendo.cloud.CommandManager<Object> manager = new org.incendo.cloud.CommandManager<>(
                org.incendo.cloud.execution.ExecutionCoordinator.simpleCoordinator(),
                org.incendo.cloud.internal.CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(Object sender, String permission) {
                return true;
            }
        };
        org.incendo.cloud.parser.flag.CommandFlag<Double> flag = BuyCommand.CONFIRM_FLAG;
        manager.command(manager.commandBuilder("buy")
                .optional("region", io.github.md5sha256.realty.command.util.RegionOrFlagParser.of(
                        org.incendo.cloud.parser.standard.StringParser.<Object>stringParser()))
                .flag(flag)
                .handler(last::set));

        manager.commandExecutor().executeCommand(new Object(), "buy c404 --confirm 1050.00").join();
        Assertions.assertEquals(1050.0, last.get().flags().getValue(flag, null));
        manager.commandExecutor().executeCommand(new Object(), "buy --confirm 99.5").join();
        Assertions.assertEquals(99.5, last.get().flags().getValue(flag, null));
    }
}
