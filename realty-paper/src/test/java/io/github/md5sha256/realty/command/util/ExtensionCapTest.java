package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.command.util.ExtensionCap.AtMost;
import io.github.md5sha256.realty.command.util.ExtensionCap.Unlimited;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionCapTest {

    private static final String PREFIX = "realty.leasehold.extensions.";

    @Mock
    private Player player;
    @Mock
    private CommandSender console;

    private final Set<PermissionAttachmentInfo> effective = new HashSet<>();

    @BeforeEach
    void setUp() {
        lenient().when(player.hasPermission(ExtensionCap.UNLIMITED_NODE)).thenReturn(false);
        lenient().when(player.getEffectivePermissions()).thenReturn(effective);
    }

    private void holds(String... nodes) {
        for (String node : nodes) {
            effective.add(new PermissionAttachmentInfo(player, node, null, true));
        }
    }

    private void holdsNegated(String node) {
        effective.add(new PermissionAttachmentInfo(player, node, null, false));
    }

    @Test
    void noNodeIsZero() {
        Assertions.assertEquals(new AtMost(0), ExtensionCap.of(player));
    }

    @Test
    void unlimitedNodeIsUnlimited() {
        when(player.hasPermission(ExtensionCap.UNLIMITED_NODE)).thenReturn(true);
        holds(PREFIX + "3");
        Assertions.assertEquals(new Unlimited(), ExtensionCap.of(player));
    }

    @Test
    void highestNumberWins() {
        holds(PREFIX + "3", PREFIX + "10", PREFIX + "5");
        Assertions.assertEquals(new AtMost(10), ExtensionCap.of(player));
    }

    @Test
    void negatedNodeIsIgnored() {
        holds(PREFIX + "3");
        holdsNegated(PREFIX + "10");
        Assertions.assertEquals(new AtMost(3), ExtensionCap.of(player));
    }

    @Test
    void malformedSuffixesAreIgnored() {
        holds(PREFIX + "abc", PREFIX + "-2", PREFIX + "99999999999", PREFIX + "4",
                "realty.leasehold.extensionsx.9", PREFIX + "unlimited");
        Assertions.assertEquals(new AtMost(4), ExtensionCap.of(player));
    }

    @Test
    void consoleIsUnlimited() {
        Assertions.assertEquals(new Unlimited(), ExtensionCap.of(console));
    }

    @Test
    void unlimitedAllowsEverything() {
        ExtensionCap cap = new Unlimited();
        Assertions.assertTrue(cap.allows(-1));
        Assertions.assertTrue(cap.allows(0));
        Assertions.assertTrue(cap.allows(1000));
    }

    @Test
    void atMostAllowsUpToItsLimit() {
        ExtensionCap cap = new AtMost(5);
        Assertions.assertTrue(cap.allows(0));
        Assertions.assertTrue(cap.allows(5));
        Assertions.assertFalse(cap.allows(6));
        Assertions.assertFalse(cap.allows(-1));
    }

    @Test
    void asLimit() {
        Assertions.assertNull(new Unlimited().asLimit());
        Assertions.assertEquals(5, new AtMost(5).asLimit());
    }
}
