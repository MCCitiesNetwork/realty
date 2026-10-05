package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.util.PartyNames;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryCommandNamesTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static PartyNames names() {
        Server server = mock(Server.class);
        Player steve = mock(Player.class);
        when(steve.getName()).thenReturn("Steve");
        when(server.getPlayer(STEVE)).thenReturn(steve);
        return new PartyNames(server, null, Clock.systemUTC());
    }

    @Test
    void presentPlayer_isShownByName() {
        assertEquals("Steve", HistoryCommand.personName(STEVE, names()));
    }

    @Test
    void missingTenant_isNotApplicable() {
        assertEquals("N/A", HistoryCommand.personName(null, names()));
    }

    @Test
    void missingBuyer_isNotApplicable() {
        assertEquals("N/A", HistoryCommand.personName(null, names()));
    }
}
