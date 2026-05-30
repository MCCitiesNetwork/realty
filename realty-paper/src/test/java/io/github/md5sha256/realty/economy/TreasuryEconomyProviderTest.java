package io.github.md5sha256.realty.economy;

import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import net.democracycraft.treasury.model.economy.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasuryEconomyProviderTest {

    @Mock
    private TreasuryApi treasuryApi;

    private TreasuryEconomyProvider provider;

    private final UUID payer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        provider = new TreasuryEconomyProvider(treasuryApi);
    }

    private Account account(int id, AccountType type, UUID owner) {
        Account a = new Account();
        a.setAccountId(id);
        a.setAccountType(type);
        a.setOwnerUuid(owner);
        return a;
    }

    private int capturedDestination(UUID recipient) {
        Account payerPersonal = account(1, AccountType.PERSONAL, payer);
        when(treasuryApi.resolveOrCreatePersonal(payer)).thenReturn(payerPersonal);
        when(treasuryApi.transfer(any())).thenReturn(99L);

        PaymentResult result = provider.transfer(payer, recipient, 50.0, "Rental Payment: REGION");
        assertInstanceOf(PaymentResult.Success.class, result);

        ArgumentCaptor<TransferRequest> req = ArgumentCaptor.forClass(TransferRequest.class);
        verify(treasuryApi).transfer(req.capture());
        assertEquals(payerPersonal.getAccountId(), req.getValue().fromAccountId());
        // amount() is normalised to scale 2; compareTo is scale-insensitive.
        assertEquals(0, new BigDecimal("50.00").compareTo(req.getValue().amount()));
        return req.getValue().toAccountId();
    }

    @Test
    void landlordWithFirm_routesToPersonalNotBusiness() {
        UUID landlord = UUID.randomUUID();
        // Landlord is a firm proprietor: owns both their PERSONAL account and a
        // firm BUSINESS account (which is owned by their own UUID).
        when(treasuryApi.getAccountsByOwner(landlord)).thenReturn(List.of(
                account(500, AccountType.BUSINESS, landlord),
                account(42, AccountType.PERSONAL, landlord)));

        assertEquals(42, capturedDestination(landlord),
                "rent must land in the landlord's personal account, not their firm");
    }

    @Test
    void legacyGovernment_withPersonalAndGovernmentAccount_routesToGovernment() {
        UUID government = UUID.randomUUID();
        // Legacy DCGovernment-style entity: a real Minecraft UUID that owns both
        // a personal account (the original player) and the government account.
        // Leasehold income must route to the government account, not personal.
        when(treasuryApi.getAccountsByOwner(government)).thenReturn(List.of(
                account(13, AccountType.PERSONAL, government),
                account(9, AccountType.GOVERNMENT, government)));

        assertEquals(9, capturedDestination(government),
                "government landlord income must route to the government account, not personal");
    }

    @Test
    void authorityUuid_withOnlyGovernmentAccount_routesToGovernment() {
        UUID authority = UUID.randomUUID();
        // Synthetic authority/government entity: no personal account exists.
        when(treasuryApi.getAccountsByOwner(authority)).thenReturn(List.of(
                account(7, AccountType.GOVERNMENT, authority)));

        assertEquals(7, capturedDestination(authority),
                "authority payments must still route to the government account");
    }

    @Test
    void recipientWithNoAccounts_resolvesOrCreatesPersonal() {
        UUID newOwner = UUID.randomUUID();
        when(treasuryApi.getAccountsByOwner(newOwner)).thenReturn(List.of());
        when(treasuryApi.resolveOrCreatePersonal(newOwner))
                .thenReturn(account(88, AccountType.PERSONAL, newOwner));

        assertEquals(88, capturedDestination(newOwner));
    }
}
