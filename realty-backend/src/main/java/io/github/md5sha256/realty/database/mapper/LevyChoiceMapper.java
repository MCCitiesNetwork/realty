package io.github.md5sha256.realty.database.mapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Base mapper interface for the levy choices: the {@code RegionLevy} table, a levy chosen for one
 * region, and the {@code AuthorityLevy} table, a levy an authority chose for all its regions.
 * SQL annotations are provided by database-specific sub-interfaces.
 */
public interface LevyChoiceMapper {

    @Nullable String selectRegionLevy(int realtyRegionId);

    @Nullable String selectAuthorityLevy(int authorityPartyId);

    int upsertRegionLevy(int realtyRegionId, @NotNull String levyName, @Nullable UUID setBy);

    int deleteRegionLevy(int realtyRegionId);

    int upsertAuthorityLevy(int partyId, @NotNull String levyName, @Nullable UUID setBy);

    int deleteAuthorityLevy(int partyId);

    /** Every levy name chosen anywhere, each once. */
    @NotNull List<String> selectAllChosenLevyNames();

}
