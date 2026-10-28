package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend.SetTitleHolderResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class TransferTitleHolderTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String freeholdHeldBy(UUID titleHolder) {
        String regionId = "transfer_region_" + REGION_COUNTER.incrementAndGet();
        Assertions.assertTrue(logic.createFreehold(regionId, WORLD_ID, 500.0, Party.personal(AUTHORITY), titleHolder));
        return regionId;
    }

    private static int historyCount(String regionId) {
        return logic.searchHistory(regionId, WORLD_ID, null, null, null, 100, 0).totalCount();
    }

    @Test
    void transferToAnotherPlayer_movesTheTitleAndClearsThePrice() {
        String regionId = freeholdHeldBy(PLAYER_A);

        SetTitleHolderResult result = logic.transferTitleHolder(regionId, WORLD_ID, PLAYER_B);

        Assertions.assertEquals(new SetTitleHolderResult.Success(PLAYER_A), result);
        Assertions.assertEquals(PLAYER_B, logic.getFreeholdContract(regionId, WORLD_ID).titleHolderId());
        Assertions.assertNull(logic.getFreeholdContract(regionId, WORLD_ID).price());
    }

    @Test
    void transferToTheCurrentHolder_isRefusedAndChangesNothing() {
        String regionId = freeholdHeldBy(PLAYER_A);
        int historyBefore = historyCount(regionId);

        SetTitleHolderResult result = logic.transferTitleHolder(regionId, WORLD_ID, PLAYER_A);

        Assertions.assertInstanceOf(SetTitleHolderResult.AlreadyTitleHolder.class, result);
        Assertions.assertEquals(PLAYER_A, logic.getFreeholdContract(regionId, WORLD_ID).titleHolderId());
        Assertions.assertEquals(500.0, logic.getFreeholdContract(regionId, WORLD_ID).price());
        Assertions.assertEquals(historyBefore, historyCount(regionId));
    }
}
