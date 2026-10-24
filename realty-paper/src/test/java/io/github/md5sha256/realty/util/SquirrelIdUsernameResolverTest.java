package io.github.md5sha256.realty.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Server;
import org.enginehub.squirrelid.Profile;
import org.enginehub.squirrelid.cache.HashMapCache;
import org.enginehub.squirrelid.cache.ProfileCache;
import org.enginehub.squirrelid.resolver.ProfileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SquirrelIdUsernameResolverTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    private Server server;
    private ProfileCache cache;
    private ProfileService remote;
    private AtomicInteger remoteLookups;
    private SquirrelIdUsernameResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        this.server = mock(Server.class);
        this.cache = new HashMapCache();
        this.remote = mock(ProfileService.class);
        this.remoteLookups = new AtomicInteger();
        when(this.remote.findByUuid(any())).thenAnswer(invocation -> {
            this.remoteLookups.incrementAndGet();
            UUID id = invocation.getArgument(0);
            return id.equals(NOTCH) ? new Profile(NOTCH, "Notch") : null;
        });
        this.resolver = new SquirrelIdUsernameResolver(this.cache, this.remote, Runnable::run, this.server);
    }

    /** The server's user cache knows {@code id} as {@code name}; null means it does not know it. */
    private void userCache(UUID id, String name) {
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.completeFromCache()).thenReturn(name != null);
        when(profile.getName()).thenReturn(name);
        when(this.server.createProfile(id)).thenReturn(profile);
    }

    @Test
    void theServerUserCacheAnswersFirstAndNeverLoadsThePlayerFile() {
        userCache(BEDROCK, ".Cool Guy 123");
        Assertions.assertEquals(".Cool Guy 123", this.resolver.getUsername(BEDROCK).join());
        Assertions.assertEquals(0, this.remoteLookups.get());
        verify(this.server, never()).getOfflinePlayer(any(UUID.class));
    }

    @Test
    void aNameFromTheUserCacheIsKeptForWhenThePlayerDropsOutOfIt() {
        userCache(BEDROCK, ".Cool Guy 123");
        this.resolver.getUsername(BEDROCK).join();
        userCache(BEDROCK, null);
        Assertions.assertEquals(".Cool Guy 123", this.resolver.getUsername(BEDROCK).join());
        Assertions.assertEquals(0, this.remoteLookups.get());
    }

    @Test
    void aRenameSeenInTheUserCacheReplacesTheKeptName() {
        this.cache.put(new Profile(NOTCH, "OldName"));
        userCache(NOTCH, "Notch");
        Assertions.assertEquals("Notch", this.resolver.getUsername(NOTCH).join());
        Assertions.assertEquals("Notch", this.cache.getIfPresent(NOTCH).getName());
    }

    @Test
    void mojangIsAskedOnlyWhenNothingLocalKnowsThePlayer() {
        userCache(NOTCH, null);
        Assertions.assertEquals("Notch", this.resolver.getUsername(NOTCH).join());
        Assertions.assertEquals(1, this.remoteLookups.get());
        Assertions.assertEquals("Notch", this.resolver.getUsername(NOTCH).join());
        Assertions.assertEquals(1, this.remoteLookups.get());
    }

    @Test
    void anUnknownPlayerResolvesToTheirUuidString() {
        UUID unknown = UUID.randomUUID();
        userCache(unknown, null);
        Assertions.assertEquals(unknown.toString(), this.resolver.getUsername(unknown).join());
    }

    @Test
    void aFailingMojangLookupResolvesToTheUuidString() throws Exception {
        userCache(NOTCH, null);
        when(this.remote.findByUuid(NOTCH)).thenThrow(new IOException("offline"));
        Assertions.assertEquals(NOTCH.toString(), this.resolver.getUsername(NOTCH).join());
    }
}
