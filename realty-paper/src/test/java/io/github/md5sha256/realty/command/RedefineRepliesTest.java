package io.github.md5sha256.realty.command;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import io.github.md5sha256.realty.api.RealtyPaperApi.RedefineRegionResult;
import io.github.md5sha256.realty.api.WorldGuardRegion;
import io.github.md5sha256.realty.api.event.RealtyNotificationEvent;
import io.github.md5sha256.realty.event.RealtyEventDispatch;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RedefineRepliesTest {

    private static final String REGION_ID = "plot";
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final WorldGuardRegion REGION = new WorldGuardRegion(
            new ProtectedCuboidRegion(REGION_ID, BlockVector3.at(0, 0, 0), BlockVector3.at(9, 9, 9)), null);

    private final CommandSender sender = Mockito.mock(CommandSender.class);
    private final RealtyEventDispatch events = Mockito.mock(RealtyEventDispatch.class);
    private final RedefineReplies replies = new RedefineReplies(ShippedMessages.load(), this.events);

    private static RedefineRegionResult.Redefined redefined(Optional<UUID> occupant, boolean captureRemoved) {
        return new RedefineRegionResult.Redefined(REGION, 1000, 125, occupant, captureRemoved);
    }

    private List<String> sent(int count) {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(this.sender, Mockito.times(count)).sendMessage(captor.capture());
        return captor.getAllValues().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    @Test
    void aRedefineRepliesWithBothSizesAndTellsTheTenant() {
        this.replies.report(this.sender, Optional.of(ACTOR), REGION_ID, redefined(Optional.of(TENANT), true));

        List<String> lines = sent(2);
        Assertions.assertTrue(lines.get(0).contains("1000") && lines.get(0).contains("125"), lines.get(0));
        Assertions.assertTrue(lines.get(1).contains(REGION_ID), lines.get(1));
        ArgumentCaptor<RealtyNotificationEvent> notice = ArgumentCaptor.forClass(RealtyNotificationEvent.class);
        verify(this.events).fireSync(notice.capture());
        Assertions.assertEquals(List.of(TENANT), notice.getValue().getTargets());
        Assertions.assertEquals(MessageKeys.NOTIFICATION_REGION_REDEFINED, notice.getValue().getMessageKey());
    }

    @Test
    void noCaptureLineWhenNoCaptureWasRemoved() {
        this.replies.report(this.sender, Optional.of(ACTOR), REGION_ID, redefined(Optional.empty(), false));

        sent(1);
        verify(this.events, never()).fireSync(any(RealtyNotificationEvent.class));
    }

    @Test
    void nobodyIsToldOfTheirOwnRedefine() {
        this.replies.report(this.sender, Optional.of(TENANT), REGION_ID, redefined(Optional.of(TENANT), false));

        verify(this.events, never()).fireSync(any(RealtyNotificationEvent.class));
    }

    @Test
    void aFailedSaveRepliesWithTheSuccessThenTheError() {
        this.replies.report(this.sender, Optional.of(ACTOR), REGION_ID,
                new RedefineRegionResult.WorldGuardSaveError(redefined(Optional.empty(), false), "disk full"));

        List<String> lines = sent(2);
        Assertions.assertTrue(lines.get(1).contains("disk full"), lines.get(1));
    }

    @Test
    void tooSmallNamesBothNumbers() {
        this.replies.report(this.sender, Optional.of(ACTOR), REGION_ID, new RedefineRegionResult.TooSmall(8, 20));

        String line = sent(1).getFirst();
        Assertions.assertTrue(line.contains("8") && line.contains("20"), line);
    }

    @Test
    void aCancelledRedefineSaysSo() {
        this.replies.report(this.sender, Optional.of(ACTOR), REGION_ID, new RedefineRegionResult.Cancelled());

        Assertions.assertEquals("That action was blocked.", sent(1).getFirst());
    }
}
