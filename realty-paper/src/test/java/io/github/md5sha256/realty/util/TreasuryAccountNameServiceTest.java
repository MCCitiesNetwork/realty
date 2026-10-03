package io.github.md5sha256.realty.util;

import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreasuryAccountNameServiceTest {

    @Mock
    private TreasuryApi treasury;

    private static Account account(int id, String displayName) {
        Account account = new Account();
        account.setAccountId(id);
        account.setDisplayName(displayName);
        return account;
    }

    @Test
    void name_isTheDisplayName() {
        when(treasury.getAccountById(42)).thenReturn(account(42, "GovSecurity"));
        var service = new TreasuryAccountNameService(treasury, Runnable::run);
        assertEquals(Optional.of("GovSecurity"), service.nameOf(42).join());
    }

    @Test
    void unknownAccount_isEmpty() {
        when(treasury.getAccountById(9)).thenReturn(null);
        var service = new TreasuryAccountNameService(treasury, Runnable::run);
        assertEquals(Optional.empty(), service.nameOf(9).join());
    }

    @Test
    void withoutTreasury_isEmpty() {
        var service = new TreasuryAccountNameService(null, Runnable::run);
        assertEquals(Optional.empty(), service.nameOf(42).join());
    }

    @Test
    void treasuryThrows_isEmpty_notExceptional() {
        when(treasury.getAccountById(42)).thenThrow(new IllegalStateException("database is down"));
        var service = new TreasuryAccountNameService(treasury, Runnable::run);
        var future = service.nameOf(42);
        assertEquals(Optional.empty(), future.join());
        assertEquals(false, future.isCompletedExceptionally());
    }

    @Test
    void namesOf_keepsTheOrderAsked() {
        when(treasury.getAccountById(2)).thenReturn(account(2, "Acme"));
        when(treasury.getAccountById(1)).thenReturn(null);
        var service = new TreasuryAccountNameService(treasury, Runnable::run);
        Map<Integer, Optional<String>> names = service.namesOf(List.of(2, 1)).join();
        assertEquals(List.of(2, 1), List.copyOf(names.keySet()));
        assertEquals(Optional.of("Acme"), names.get(2));
        assertEquals(Optional.empty(), names.get(1));
    }
}
