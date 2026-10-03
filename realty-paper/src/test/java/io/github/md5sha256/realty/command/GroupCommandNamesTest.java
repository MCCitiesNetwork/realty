package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.util.PartyNames;
import net.democracycraft.treasury.api.TreasuryApi;
import net.democracycraft.treasury.model.economy.Account;
import net.democracycraft.treasury.model.economy.AccountType;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupCommandNamesTest {

    private static PartyNames names(Account account) {
        TreasuryApi treasury = mock(TreasuryApi.class);
        when(treasury.getAccountById(account.getAccountId())).thenReturn(account);
        return new PartyNames(mock(Server.class), treasury, Clock.systemUTC());
    }

    private static Account account(int id, AccountType type, String displayName) {
        Account account = new Account();
        account.setAccountId(id);
        account.setAccountType(type);
        account.setDisplayName(displayName);
        return account;
    }

    @Test
    void groupAccount_isShownAsEverywhereElse() {
        PartyNames names = names(account(7, AccountType.GOVERNMENT, "GovSecurity"));
        Party.Group police = new Party.Group("police", 7, AccountKind.GOVERNMENT);
        assertEquals("GovSecurity (government)", GroupCommandGroup.accountName(police, names));
    }

    @Test
    void groupAccountWithoutAName_isShownByItsId() {
        PartyNames names = names(account(7, AccountType.BUSINESS, ""));
        Party.Group police = new Party.Group("police", 7, AccountKind.BUSINESS);
        assertEquals("#7 (business)", GroupCommandGroup.accountName(police, names));
    }
}
