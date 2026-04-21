package io.github.md5sha256.realty.database.maria.mapper;

import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.mapper.SearchMapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MariaSearchMapper extends SearchMapper {

    @Override
    @Select("""
            <script>
            SELECT * FROM (
                <if test="includeFreehold">
                SELECT rr.worldGuardRegionId, rr.worldId, 'freehold' AS contractType, fc.price
                FROM RealtyRegion rr
                INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'freehold'
                INNER JOIN FreeholdContract fc ON fc.freeholdContractId = c.contractId
                WHERE fc.price IS NOT NULL
                    AND fc.price &gt;= #{minPrice}
                    AND fc.price &lt;= #{maxPrice}
                    <if test="tagIds != null and tagIds.size() > 0">
                    AND EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="tagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                    <if test="excludedTagIds != null and excludedTagIds.size() > 0">
                    AND NOT EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="excludedTagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                </if>
                <if test="includeFreehold and includeLeasehold">
                UNION ALL
                </if>
                <if test="includeLeasehold">
                SELECT rr.worldGuardRegionId, rr.worldId, 'leasehold' AS contractType, lc.price
                FROM RealtyRegion rr
                INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'leasehold'
                INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = c.contractId
                WHERE lc.price &gt;= #{minPrice}
                    AND lc.price &lt;= #{maxPrice}
                    <if test="excludeRented">
                    AND lc.tenantId IS NULL
                    </if>
                    <if test="tagIds != null and tagIds.size() > 0">
                    AND EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="tagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                    <if test="excludedTagIds != null and excludedTagIds.size() > 0">
                    AND NOT EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="excludedTagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                </if>
            ) AS results
            ORDER BY price DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ConstructorArgs({
            @Arg(column = "worldGuardRegionId", javaType = String.class),
            @Arg(column = "worldId", javaType = UUID.class),
            @Arg(column = "contractType", javaType = String.class),
            @Arg(column = "price", javaType = double.class)
    })
    @NotNull List<SearchResultEntity> search(@Param("includeFreehold") boolean includeFreehold,
                                             @Param("includeLeasehold") boolean includeLeasehold,
                                             @Param("tagIds") @Nullable Collection<String> tagIds,
                                             @Param("excludedTagIds") @Nullable Collection<String> excludedTagIds,
                                             @Param("excludeRented") boolean excludeRented,
                                             @Param("minPrice") double minPrice,
                                             @Param("maxPrice") double maxPrice,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    @Override
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
                <if test="includeFreehold">
                SELECT rr.realtyRegionId
                FROM RealtyRegion rr
                INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'freehold'
                INNER JOIN FreeholdContract fc ON fc.freeholdContractId = c.contractId
                WHERE fc.price IS NOT NULL
                    AND fc.price &gt;= #{minPrice}
                    AND fc.price &lt;= #{maxPrice}
                    <if test="tagIds != null and tagIds.size() > 0">
                    AND EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="tagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                    <if test="excludedTagIds != null and excludedTagIds.size() > 0">
                    AND NOT EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="excludedTagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                </if>
                <if test="includeFreehold and includeLeasehold">
                UNION ALL
                </if>
                <if test="includeLeasehold">
                SELECT rr.realtyRegionId
                FROM RealtyRegion rr
                INNER JOIN Contract c ON c.realtyRegionId = rr.realtyRegionId AND c.contractType = 'leasehold'
                INNER JOIN LeaseholdContract lc ON lc.leaseholdContractId = c.contractId
                WHERE lc.price &gt;= #{minPrice}
                    AND lc.price &lt;= #{maxPrice}
                    <if test="excludeRented">
                    AND lc.tenantId IS NULL
                    </if>
                    <if test="tagIds != null and tagIds.size() > 0">
                    AND EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="tagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                    <if test="excludedTagIds != null and excludedTagIds.size() > 0">
                    AND NOT EXISTS (
                        SELECT 1 FROM RegionTag rt
                        WHERE rt.worldGuardRegionId = rr.worldGuardRegionId
                        AND rt.tagId IN
                        <foreach item="tag" collection="excludedTagIds" open="(" separator="," close=")">
                            #{tag}
                        </foreach>
                    )
                    </if>
                </if>
            ) AS results
            </script>
            """)
    int searchCount(@Param("includeFreehold") boolean includeFreehold,
                    @Param("includeLeasehold") boolean includeLeasehold,
                    @Param("tagIds") @Nullable Collection<String> tagIds,
                    @Param("excludedTagIds") @Nullable Collection<String> excludedTagIds,
                    @Param("excludeRented") boolean excludeRented,
                    @Param("minPrice") double minPrice,
                    @Param("maxPrice") double maxPrice);

}
