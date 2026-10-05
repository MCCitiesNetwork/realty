package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class ActorContextTest {

    private static final Party GOV = Party.account(42, AccountKind.GOVERNMENT);

    @Test
    void player_managesAndReassignsThemself() {
        UUID player = UUID.randomUUID();
        ActorContext ctx = ActorContext.player(player, false);

        Assertions.assertEquals(player, ctx.requirePlayer());
        Assertions.assertTrue(ctx.mayManage(Party.personal(player)));
        Assertions.assertTrue(ctx.mayReassign(Party.personal(player)));
        Assertions.assertFalse(ctx.mayManage(GOV));
        Assertions.assertFalse(ctx.mayReassign(GOV));
        Assertions.assertFalse(ctx.mayManage(Party.personal(UUID.randomUUID())));
    }

    @Test
    void console_bypassesAndHasNoPlayer() {
        ActorContext ctx = ActorContext.console();

        Assertions.assertNull(ctx.player());
        Assertions.assertTrue(ctx.bypass());
        Assertions.assertTrue(ctx.manages().isEmpty());
        Assertions.assertTrue(ctx.mayManage(GOV));
        Assertions.assertTrue(ctx.mayReassign(GOV));
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, ctx::requirePlayer);
        Assertions.assertEquals("this action needs a player", thrown.getMessage());
    }

    @Test
    void setsAreCopied() {
        UUID player = UUID.randomUUID();
        Set<Party> manages = new HashSet<>();
        Set<Party> reassigns = new HashSet<>();
        ActorContext ctx = new ActorContext(player, manages, reassigns, false);

        manages.add(GOV);
        reassigns.add(GOV);

        Assertions.assertFalse(ctx.mayManage(GOV));
        Assertions.assertFalse(ctx.mayReassign(GOV));
        Assertions.assertEquals(Set.of(Party.personal(player)), ctx.manages());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> ctx.manages().add(GOV));
    }
}
