package io.github.md5sha256.realty.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.rest.json.PartyRef;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.PartyNames;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How each kind of party is written into a response. The mapper is configured exactly as
 * the server configures its own.
 */
class PartyRefTest {

    private static final UUID STEVE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static @NotNull String json(@NotNull ModuleClient module, @NotNull Party party) throws Exception {
        return MAPPER.writeValueAsString(PartyNames.resolve(module, List.of(party)).ref(party));
    }

    @Test
    void personal_serialises() throws Exception {
        ModuleClient module = TestServers.stubModule(Map.of(STEVE, "Steve"), Map.of(), Map.of(), Map.of());
        Assertions.assertEquals("{\"kind\":\"personal\",\"id\":\"" + STEVE + "\",\"name\":\"Steve\"}",
                json(module, new Party.Personal(STEVE)));
    }

    @Test
    void government_serialises() throws Exception {
        ModuleClient module = TestServers.stubModule(Map.of(), Map.of(42, "GovSecurity"), Map.of(), Map.of());
        Assertions.assertEquals("{\"kind\":\"government\",\"id\":\"42\",\"name\":\"GovSecurity\"}",
                json(module, new Party.Account(42, AccountKind.GOVERNMENT)));
    }

    @Test
    void group_serialises() throws Exception {
        ModuleClient module = TestServers.stubModule(Map.of(), Map.of(), Map.of(), Map.of());
        Assertions.assertEquals("{\"kind\":\"group\",\"id\":\"police\",\"name\":\"police\"}",
                json(module, new Party.Group("police", 7, AccountKind.GOVERNMENT)));
    }

    @Test
    void accountWithoutAName_hasANullName() throws Exception {
        ModuleClient module = TestServers.stubModule(Map.of(), Map.of(), Map.of(), Map.of());
        Assertions.assertEquals("{\"kind\":\"business\",\"id\":\"77\",\"name\":null}",
                json(module, new Party.Account(77, AccountKind.BUSINESS)));
    }

    @Test
    void accountNamesAreNullWhenTheModuleIsDisabled() throws Exception {
        Assertions.assertEquals("{\"kind\":\"system\",\"id\":\"5\",\"name\":null}",
                json(ModuleClient.disabled(), new Party.Account(5, AccountKind.SYSTEM)));
    }

    @Test
    void nullParty_isANullRef() {
        PartyNames.Resolved resolved = PartyNames.resolve(ModuleClient.disabled(), List.of());
        Assertions.assertNull(resolved.ref((Party) null));
        Assertions.assertNull(resolved.ref((UUID) null));
    }

    @Test
    void aPlayerUuidIsAPersonalRef() {
        ModuleClient module = TestServers.stubModule(Map.of(STEVE, "Steve"), Map.of(), Map.of(), Map.of());
        PartyNames.Resolved resolved = PartyNames.resolve(module, List.of(new Party.Personal(STEVE)));
        Assertions.assertEquals(PartyRef.personal(STEVE, "Steve"), resolved.ref(STEVE));
    }

    @Test
    void aNameIsReturnedAsTextAndNeverInterpreted() throws Exception {
        String chosen = "<b>\"Gov\"</b> {x}";
        ModuleClient module = TestServers.stubModule(Map.of(), Map.of(9, chosen), Map.of(), Map.of());
        String body = json(module, new Party.Account(9, AccountKind.GOVERNMENT));
        Assertions.assertEquals(chosen, MAPPER.readTree(body).path("name").asText());
    }
}
