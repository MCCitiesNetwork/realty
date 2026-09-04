package io.github.md5sha256.realty.rest.module;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class ModuleClientDisabledTest {

    @Test
    void everyCallDegradesWithoutTouchingTheNetwork() {
        ModuleClient client = ModuleClient.disabled();
        Assertions.assertEquals(Optional.empty(), client.dimensions(UUID.randomUUID(), "plot"));
        Assertions.assertTrue(client.names(List.of(UUID.randomUUID())).isEmpty());
        Assertions.assertInstanceOf(NameLookup.Unavailable.class, client.uuidOf("Notch"));
        Assertions.assertEquals(ModuleClient.Status.DISABLED, client.status());
    }
}
