package io.github.md5sha256.realty;

import io.github.md5sha256.realty.economy.EconomyProvider;
import io.paradaux.treasury.api.TreasuryApi;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryServiceResolutionTest {

    private Realty plugin;
    private PluginManager plugins;
    private ServicesManager services;

    @BeforeEach
    void setUp() {
        plugin = mock(Realty.class);
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        services = mock(ServicesManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getPluginManager()).thenReturn(plugins);
        when(server.getServicesManager()).thenReturn(services);
    }

    private EconomyProvider resolve() throws Exception {
        var method = Realty.class.getDeclaredMethod("resolveEconomyProvider");
        method.setAccessible(true);
        return (EconomyProvider) method.invoke(plugin);
    }

    private void registerVault() {
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(services.getRegistration(Economy.class)).thenReturn(registration);
        when(registration.getProvider()).thenReturn(mock(Economy.class));
    }

    @Test
    void prefersTheHiberniaTreasuryService() throws Exception {
        when(plugins.isPluginEnabled("Treasury")).thenReturn(true);
        RegisteredServiceProvider<TreasuryApi> registration = mock(RegisteredServiceProvider.class);
        when(services.getRegistration(TreasuryApi.class)).thenReturn(registration);
        when(registration.getProvider()).thenReturn(mock(TreasuryApi.class));

        assertTrue(resolve().hasLedgerSupport());
        verify(services).getRegistration(TreasuryApi.class);
        verify(services, never()).getRegistration(Economy.class);
    }

    @Test
    void usesVaultWhenTreasuryIsNotEnabled() throws Exception {
        registerVault();

        assertFalse(resolve().hasLedgerSupport());
        verify(services, never()).getRegistration(TreasuryApi.class);
    }

    @Test
    void usesVaultWhenTreasuryHasNoRegisteredService() throws Exception {
        when(plugins.isPluginEnabled("Treasury")).thenReturn(true);
        registerVault();

        assertFalse(resolve().hasLedgerSupport());
        verify(services).getRegistration(TreasuryApi.class);
    }

    @Test
    void returnsNoProviderWhenNeitherEconomyIsAvailable() throws Exception {
        assertNull(resolve());
    }
}
