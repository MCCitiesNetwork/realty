package io.github.md5sha256.realty.economy;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultEconomyProviderTest {

    @Mock
    private Economy economy;

    private MockedStatic<Bukkit> bukkitMock;
    private VaultEconomyProvider provider;

    private final UUID payerId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final OfflinePlayer payer = mock(OfflinePlayer.class);
    private final OfflinePlayer recipient = mock(OfflinePlayer.class);

    @BeforeEach
    void setUp() {
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(() -> Bukkit.getOfflinePlayer(payerId)).thenReturn(payer);
        bukkitMock.when(() -> Bukkit.getOfflinePlayer(recipientId)).thenReturn(recipient);
        provider = new VaultEconomyProvider(economy);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    private static EconomyResponse ok(double amount) {
        return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Test
    void accountParty_isRefused() {
        Party government = Party.account(42, AccountKind.GOVERNMENT);
        Party group = Party.group("police", 42, AccountKind.GOVERNMENT);

        assertEquals(new PaymentResult.Failure("Account and group parties require Treasury"),
                provider.transfer(Party.personal(payerId), government, 50.0, "Rental Payment: REGION", payerId));
        assertEquals(new PaymentResult.Failure("Account and group parties require Treasury"),
                provider.transfer(group, Party.personal(payerId), 50.0, "Refund: REGION", payerId));
        verify(economy, never()).withdrawPlayer(any(OfflinePlayer.class), anyDouble());
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void accountParty_hasNoBalance() {
        assertEquals(0.0, provider.getBalance(Party.account(42, AccountKind.GOVERNMENT)));
        verifyNoInteractions(economy);
    }

    @Test
    void players_stillPayEachOther() {
        when(economy.withdrawPlayer(payer, 50.0)).thenReturn(ok(50.0));
        when(economy.depositPlayer(recipient, 50.0)).thenReturn(ok(50.0));

        PaymentResult result = provider.transfer(Party.personal(payerId), Party.personal(recipientId),
                50.0, "Rental Payment: REGION", payerId);

        assertInstanceOf(PaymentResult.Success.class, result);
        verify(economy).withdrawPlayer(payer, 50.0);
        verify(economy).depositPlayer(recipient, 50.0);
    }

    @Test
    void playerBalance_isReadFromVault() {
        when(economy.getBalance(payer)).thenReturn(75.0);

        assertEquals(75.0, provider.getBalance(Party.personal(payerId)));
    }
}
