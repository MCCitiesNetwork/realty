package io.github.md5sha256.realty.util;

import com.minecraftcitiesnetwork.namebook.api.Namebook;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NamebookPlayerNameServiceTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    @Test
    void aBatchIsOneNamebookBulkCallNotOneCallPerId() {
        Namebook namebook = mock(Namebook.class);
        Map<UUID, Optional<String>> answer = Map.of(NOTCH, Optional.of("Notch"), BEDROCK, Optional.empty());
        when(namebook.namesOf(any())).thenReturn(CompletableFuture.completedFuture(answer));

        Map<UUID, Optional<String>> names = new NamebookPlayerNameService(namebook)
                .namesOf(List.of(NOTCH, BEDROCK)).join();

        Assertions.assertEquals(answer, names);
        verify(namebook).namesOf(List.of(NOTCH, BEDROCK));
        verify(namebook, never()).nameOf(any());
    }

    @Test
    void findIsNullWhenNamebookHasNotRegisteredItsService() {
        ServicesManager services = mock(ServicesManager.class);
        Assertions.assertNull(NamebookPlayerNameService.find(services));
    }

    @Test
    void findUsesTheRegisteredService() {
        Namebook namebook = mock(Namebook.class);
        when(namebook.nameOf(NOTCH)).thenReturn(CompletableFuture.completedFuture(Optional.of("Notch")));
        ServicesManager services = mock(ServicesManager.class);
        when(services.load(Namebook.class)).thenReturn(namebook);

        Assertions.assertEquals(Optional.of("Notch"), NamebookPlayerNameService.find(services).nameOf(NOTCH).join());
    }
}
