package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.api.RealtyBackend.AcceptAgentInviteResult;
import io.github.md5sha256.realty.api.RealtyBackend.InviteAgentResult;
import io.github.md5sha256.realty.api.RealtyBackend.RejectAgentInviteResult;
import io.github.md5sha256.realty.api.RealtyBackend.WithdrawAgentInviteResult;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class AgentLogicTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.randomUUID();
    private static final UUID AUTHORITY = UUID.randomUUID();
    private static final UUID TITLE_HOLDER = UUID.randomUUID();
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();

    private static final AtomicInteger REGION_COUNTER = new AtomicInteger();

    private static String uniqueRegionId() {
        return "agent_region_" + REGION_COUNTER.incrementAndGet();
    }

    private static void createFreeholdRegion(String regionId) {
        boolean created = logic.createFreehold(regionId, WORLD_ID, 1000.0, AUTHORITY, TITLE_HOLDER);
        Assertions.assertTrue(created, "Expected freehold region to be created");
    }

    private static void inviteAndAcceptAgent(String regionId, UUID inviteeId) {
        InviteAgentResult invite = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, inviteeId);
        Assertions.assertInstanceOf(InviteAgentResult.Success.class, invite);
        AcceptAgentInviteResult accept = logic.acceptAgentInvite(regionId, WORLD_ID, inviteeId);
        Assertions.assertInstanceOf(AcceptAgentInviteResult.Success.class, accept);
    }

    // --- Invite Agent ---

    @Nested
    @DisplayName("inviteAgent")
    class InviteAgent {

        @Test
        @DisplayName("succeeds for a valid invite")
        void succeeds() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.Success.class, result);
        }

        @Test
        @DisplayName("returns NoFreeholdContract when region has no freehold")
        void noFreeholdContract() {
            InviteAgentResult result = logic.inviteAgent("nonexistent", WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.NoFreeholdContract.class, result);
        }

        @Test
        @DisplayName("returns NotTitleHolder when inviter is not the title holder")
        void notTitleHolder() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, PLAYER_A, PLAYER_B);
            Assertions.assertInstanceOf(InviteAgentResult.NotTitleHolder.class, result);
        }

        @Test
        @DisplayName("returns IsTitleHolder when inviting self (title holder)")
        void cannotInviteSelfAsTitleHolder() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, TITLE_HOLDER);
            Assertions.assertInstanceOf(InviteAgentResult.IsTitleHolder.class, result);
        }

        @Test
        @DisplayName("returns IsAuthority when inviting the authority")
        void cannotInviteAuthority() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, AUTHORITY);
            Assertions.assertInstanceOf(InviteAgentResult.IsAuthority.class, result);
        }

        @Test
        @DisplayName("returns AlreadyAgent when invitee is already a sanctioned auctioneer")
        void alreadyAgent() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            inviteAndAcceptAgent(regionId, PLAYER_A);

            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.AlreadyAgent.class, result);
        }

        @Test
        @DisplayName("returns AlreadyInvited when invite is already pending")
        void alreadyInvited() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.AlreadyInvited.class, result);
        }

        @Test
        @DisplayName("can invite multiple different players")
        void multipleInvites() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            InviteAgentResult resultA = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            InviteAgentResult resultB = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_B);
            Assertions.assertInstanceOf(InviteAgentResult.Success.class, resultA);
            Assertions.assertInstanceOf(InviteAgentResult.Success.class, resultB);
        }
    }

    // --- Accept Agent Invite ---

    @Nested
    @DisplayName("acceptAgentInvite")
    class AcceptAgentInvite {

        @Test
        @DisplayName("succeeds and returns inviter ID")
        void succeeds() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);

            AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.Success.class, result);
            AcceptAgentInviteResult.Success success = (AcceptAgentInviteResult.Success) result;
            Assertions.assertEquals(TITLE_HOLDER, success.inviterId());
        }

        @Test
        @DisplayName("returns NotFound when no invite exists")
        void notFound() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.NotFound.class, result);
        }

        @Test
        @DisplayName("returns AlreadyAgent when player is already a sanctioned auctioneer")
        void alreadyAgent() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            inviteAndAcceptAgent(regionId, PLAYER_A);

            // Manually insert a second invite to simulate the edge case
            try (SqlSessionWrapper wrapper = database.openSession();
                 SqlSession session = wrapper.session()) {
                wrapper.freeholdContractAgentInviteMapper()
                        .insert(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
                session.commit();
            }

            AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.AlreadyAgent.class, result);
        }

        @Test
        @DisplayName("accepting removes the pending invite")
        void removesInvite() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);

            // Accepting again should find no invite
            AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.NotFound.class, result);
        }
    }

    // --- Withdraw Agent Invite ---

    @Nested
    @DisplayName("withdrawAgentInvite")
    class WithdrawAgentInviteTest {

        @Test
        @DisplayName("succeeds when invite exists")
        void succeeds() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);

            WithdrawAgentInviteResult result = logic.withdrawAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(WithdrawAgentInviteResult.Success.class, result);
        }

        @Test
        @DisplayName("returns NotFound when no invite exists")
        void notFound() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            WithdrawAgentInviteResult result = logic.withdrawAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(WithdrawAgentInviteResult.NotFound.class, result);
        }

        @Test
        @DisplayName("withdrawing prevents future acceptance")
        void preventsAcceptance() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            logic.withdrawAgentInvite(regionId, WORLD_ID, PLAYER_A);

            AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.NotFound.class, result);
        }
    }

    // --- Reject Agent Invite ---

    @Nested
    @DisplayName("rejectAgentInvite")
    class RejectAgentInviteTest {

        @Test
        @DisplayName("succeeds and returns inviter ID")
        void succeeds() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);

            RejectAgentInviteResult result = logic.rejectAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(RejectAgentInviteResult.Success.class, result);
            RejectAgentInviteResult.Success success = (RejectAgentInviteResult.Success) result;
            Assertions.assertEquals(TITLE_HOLDER, success.inviterId());
        }

        @Test
        @DisplayName("returns NotFound when no invite exists")
        void notFound() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            RejectAgentInviteResult result = logic.rejectAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(RejectAgentInviteResult.NotFound.class, result);
        }

        @Test
        @DisplayName("rejecting prevents future acceptance")
        void preventsAcceptance() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            logic.rejectAgentInvite(regionId, WORLD_ID, PLAYER_A);

            AcceptAgentInviteResult result = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.NotFound.class, result);
        }
    }

    // --- Remove Sanctioned Auctioneer ---

    @Nested
    @DisplayName("removeSanctionedAuctioneer")
    class RemoveSanctioneerTest {

        @Test
        @DisplayName("succeeds when agent exists")
        void succeeds() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            inviteAndAcceptAgent(regionId, PLAYER_A);

            int rows = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);
            Assertions.assertEquals(1, rows);
        }

        @Test
        @DisplayName("returns 0 when agent does not exist")
        void notFound() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            int rows = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);
            Assertions.assertEquals(0, rows);
        }

        @Test
        @DisplayName("removing an agent allows re-inviting")
        void canReInviteAfterRemoval() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            inviteAndAcceptAgent(regionId, PLAYER_A);

            logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);

            InviteAgentResult result = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.Success.class, result);
        }

        @Test
        @DisplayName("removing is idempotent - second removal returns 0")
        void doubleRemoval() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);
            inviteAndAcceptAgent(regionId, PLAYER_A);

            int first = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);
            int second = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);
            Assertions.assertEquals(1, first);
            Assertions.assertEquals(0, second);
        }
    }

    // --- Full Lifecycle ---

    @Nested
    @DisplayName("agent lifecycle")
    class AgentLifecycle {

        @Test
        @DisplayName("full invite -> accept -> remove cycle")
        void fullCycle() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            // Invite
            InviteAgentResult invite = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.Success.class, invite);

            // Accept
            AcceptAgentInviteResult accept = logic.acceptAgentInvite(regionId, WORLD_ID, PLAYER_A);
            Assertions.assertInstanceOf(AcceptAgentInviteResult.Success.class, accept);

            // Cannot invite again (already agent)
            InviteAgentResult duplicate = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.AlreadyAgent.class, duplicate);

            // Remove
            int rows = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);
            Assertions.assertEquals(1, rows);

            // Can invite again after removal
            InviteAgentResult reInvite = logic.inviteAgent(regionId, WORLD_ID, TITLE_HOLDER, PLAYER_A);
            Assertions.assertInstanceOf(InviteAgentResult.Success.class, reInvite);
        }

        @Test
        @DisplayName("multiple agents on the same region")
        void multipleAgents() {
            String regionId = uniqueRegionId();
            createFreeholdRegion(regionId);

            inviteAndAcceptAgent(regionId, PLAYER_A);
            inviteAndAcceptAgent(regionId, PLAYER_B);

            // Both should be removable independently
            int rowsA = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_A, TITLE_HOLDER);
            int rowsB = logic.removeSanctionedAuctioneer(regionId, WORLD_ID, PLAYER_B, TITLE_HOLDER);
            Assertions.assertEquals(1, rowsA);
            Assertions.assertEquals(1, rowsB);
        }
    }
}
