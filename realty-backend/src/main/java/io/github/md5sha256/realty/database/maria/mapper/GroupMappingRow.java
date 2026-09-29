package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.database.entity.GroupMapping;
import org.jetbrains.annotations.NotNull;

/**
 * One row of {@link MariaPartyMapper#selectGroupMappingRows}, read column by column and then
 * turned into a {@link GroupMapping}.
 *
 * <p>Public for the same reason as {@link PartyAccountRow}: it is the element type of a method
 * on a public mapper interface, which the proxy MyBatis builds must be able to reference.</p>
 */
public record GroupMappingRow(int partyId,
                              @NotNull String groupName,
                              int groupAccountId,
                              @NotNull AccountKind groupAccountKind,
                              int contractCount) {

    @NotNull GroupMapping toGroupMapping() {
        return new GroupMapping(new Party.Group(groupName, groupAccountId, groupAccountKind), contractCount);
    }
}
