package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

class PartyRegionsEndpointTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID PLAYER = TestServers.PLAYER_ID;
    private static final Party.Account GOVERNMENT = new Party.Account(TestServers.ACCOUNT_ID, AccountKind.GOVERNMENT);
    private static final Party.Group POLICE = new Party.Group("police", 7, AccountKind.GOVERNMENT);

    private static RealtyRegionEntity region(String name) {
        return new RealtyRegionEntity(1, name, WORLD_ID);
    }

    /** A party that is the authority of one region and nothing else. */
    private static RealtyBackend.ListResult authorityOf(String name) {
        return new RealtyBackend.ListResult(0, 1, 0, List.of(), List.of(region(name)), List.of());
    }

    @Test
    void government_listsItsRegions() {
        TestServers.PartyStub stub = new TestServers.PartyStub();
        stub.lists.put(GOVERNMENT, authorityOf("town_hall"));
        JavalinTest.test(TestServers.withPartyHoldings(stub, 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/government/" + TestServers.ACCOUNT_ID + "/regions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"player\":{\"kind\":\"government\",\"id\":\"42\""), body);
            Assertions.assertTrue(body.contains("\"town_hall\""), body);
            Assertions.assertTrue(body.contains("\"owned\":[]"), body);
            Assertions.assertTrue(body.contains("\"rented\":[]"), body);
        });
    }

    @Test
    void personal_matchesThePlayersRoute() {
        TestServers.PartyStub stub = new TestServers.PartyStub();
        stub.lists.put(new Party.Personal(PLAYER), new RealtyBackend.ListResult(
                1, 1, 0, List.of(region("owned_plot")), List.of(region("landlord_plot")), List.of()));
        JavalinTest.test(TestServers.withPartyHoldings(stub, 100).javalin(), (server, client) -> {
            for (String query : List.of("", "?category=owned", "?category=rented", "?pageSize=1&page=2")) {
                String viaPlayer = client.get("/v1/players/regions" + (query.isEmpty() ? "?" : query + "&")
                        + "player=" + PLAYER).body().string();
                String viaParty = client.get("/v1/parties/personal/" + PLAYER + "/regions" + query).body().string();
                Assertions.assertEquals(viaPlayer, viaParty, query);
                Assertions.assertTrue(viaParty.contains("\"kind\":\"personal\""), query);
            }
            String all = client.get("/v1/parties/personal/" + PLAYER + "/regions").body().string();
            Assertions.assertTrue(all.contains("\"owned_plot\""), all);
            Assertions.assertTrue(all.contains("\"landlord_plot\""), all);
            Assertions.assertTrue(all.contains("\"rented_plot\""), all);
        });
    }

    @Test
    void group_listsItsRegions() {
        TestServers.PartyStub stub = new TestServers.PartyStub();
        stub.groups.put("police", POLICE);
        stub.lists.put(POLICE, authorityOf("station"));
        JavalinTest.test(TestServers.withPartyHoldings(stub, 100).javalin(), (server, client) -> {
            for (String id : List.of("police", "Police", "POLICE")) {
                Response response = client.get("/v1/parties/group/" + id + "/regions");
                Assertions.assertEquals(200, response.code(), id);
                String body = response.body().string();
                Assertions.assertTrue(body.contains("\"player\":{\"kind\":\"group\",\"id\":\"police\",\"name\":\"police\"}"), body);
                Assertions.assertTrue(body.contains("\"station\""), body);
            }
        });
    }

    @Test
    void unknownKind_is400() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            for (String kind : List.of("planet", "Government", "PERSONAL", "account")) {
                Response response = client.get("/v1/parties/" + kind + "/42/regions");
                Assertions.assertEquals(400, response.code(), kind);
                Assertions.assertTrue(response.body().string().contains("INVALID_PARTY_KIND"), kind);
            }
        });
    }

    @Test
    void personalIdThatIsNotAUuid_is400AndIsNotEchoed() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            for (String id : List.of("Notch", "1-1-1-1-1", "3a1c88f0-0000-0000-0000-00000000zzzz")) {
                Response response = client.get("/v1/parties/personal/" + id + "/regions");
                Assertions.assertEquals(400, response.code(), id);
                String body = response.body().string();
                Assertions.assertTrue(body.contains("MALFORMED_UUID"), body);
                Assertions.assertFalse(body.contains(id), body);
            }
        });
    }

    @Test
    void accountIdThatIsNotANumber_is400() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            for (String id : List.of("abc", "-5", "0", "+5", "4.2", "99999999999", "%3Cscript%3E")) {
                Response response = client.get("/v1/parties/business/" + id + "/regions");
                Assertions.assertEquals(400, response.code(), id);
                String body = response.body().string();
                Assertions.assertTrue(body.contains("INVALID_ACCOUNT_ID"), body);
                Assertions.assertFalse(body.contains(id) || body.contains("<script>"), body);
            }
        });
    }

    @Test
    void unmappedGroup_is404() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/group/nobody/regions");
            Assertions.assertEquals(404, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("PARTY_NOT_FOUND"), body);
            Assertions.assertFalse(body.contains("nobody"), body);
        });
    }

    @Test
    void accountNobodyNames_isEmptyNot404() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/system/9999/regions");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":0"), body);
            Assertions.assertTrue(body.contains("\"landlord\":[]"), body);
        });
    }

    @Test
    void accountStoredUnderAnotherKind_is404() {
        TestServers.PartyStub stub = new TestServers.PartyStub();
        stub.accounts.put(TestServers.ACCOUNT_ID, GOVERNMENT);
        JavalinTest.test(TestServers.withPartyHoldings(stub, 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/business/" + TestServers.ACCOUNT_ID + "/regions");
            Assertions.assertEquals(404, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("PARTY_NOT_FOUND"), body);
            Assertions.assertFalse(body.contains("42"), body);
            Assertions.assertEquals(List.of(), stub.asked);
        });
    }

    @Test
    void accountStoredUnderTheSameKind_listsItsRegions() {
        TestServers.PartyStub stub = new TestServers.PartyStub();
        stub.accounts.put(TestServers.ACCOUNT_ID, GOVERNMENT);
        stub.lists.put(GOVERNMENT, authorityOf("town_hall"));
        JavalinTest.test(TestServers.withPartyHoldings(stub, 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/government/" + TestServers.ACCOUNT_ID + "/regions");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"town_hall\""));
        });
    }

    @Test
    void accountNobodyNames_asksTheModuleForNothing() {
        List<String> moduleCalls = new java.util.concurrent.CopyOnWriteArrayList<>();
        ModuleClient module = (ModuleClient) Proxy.newProxyInstance(ModuleClient.class.getClassLoader(),
                new Class<?>[]{ModuleClient.class}, (proxy, method, args) -> {
                    moduleCalls.add(method.getName());
                    throw new AssertionError("the module was asked: " + method.getName());
                });
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100, module).javalin(),
                (server, client) -> {
                    Response response = client.get("/v1/parties/business/7/regions");
                    Assertions.assertEquals(200, response.code());
                    String body = response.body().string();
                    Assertions.assertTrue(body.contains("\"player\":{\"kind\":\"business\",\"id\":\"7\",\"name\":null}"), body);
                    Assertions.assertTrue(body.contains("\"totalCount\":0"), body);
                    Assertions.assertEquals(List.of(), moduleCalls);
                });
    }

    @Test
    void unknownCategory_is400AndIsNotEchoed() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/business/42/regions?category=owned2");
            Assertions.assertEquals(400, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("INVALID_CATEGORY"), body);
            Assertions.assertFalse(body.contains("owned2"), body);
        });
    }

    @Test
    void pageSoLargeItsOffsetOverflows_is400Not500() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 100).javalin(), (server, client) -> {
            Response response = client.get("/v1/parties/business/42/regions?page=2147483647");
            Assertions.assertEquals(400, response.code());
            Assertions.assertTrue(response.body().string().contains("INVALID_PAGE"));
        });
    }

    @Test
    void pageSize_isClamped() {
        JavalinTest.test(TestServers.withPartyHoldings(new TestServers.PartyStub(), 10).javalin(), (server, client) -> {
            String body = client.get("/v1/parties/business/42/regions?pageSize=500").body().string();
            Assertions.assertTrue(body.contains("\"pageSize\":10"), body);
        });
    }

    @Test
    void anAccountIsListedAsTheKindTheCallerNamed() {
        TestServers.PartyStub stub = new TestServers.PartyStub();
        JavalinTest.test(TestServers.withPartyHoldings(stub, 100).javalin(), (server, client) -> {
            client.get("/v1/parties/business/42/regions");
            Assertions.assertEquals(List.of(new Party.Account(42, AccountKind.BUSINESS)), stub.asked);
        });
    }
}
