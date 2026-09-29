package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.api.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PartyMapper {

    /** The id of the party's row, inserting the row if needed. A Group is never inserted here. */
    int findOrInsert(@NotNull Party party);

    /** A Group is found by groupName alone. */
    @Nullable Integer findId(@NotNull Party party);

    @Nullable Party selectById(int partyId);

    /** Every party that is not PERSONAL, in the order the rows were created. */
    @NotNull List<Party> selectNonPersonal();
}
