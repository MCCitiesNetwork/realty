package io.github.md5sha256.realty.database.mapper;

import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import org.jetbrains.annotations.NotNull;

/**
 * The estate's totals as one row.
 *
 * <p>Deliberately a mapper of its own rather than a method on each contract mapper:
 * the point is that all ten figures come back from one statement, and a method that
 * lives on the freehold mapper but counts auctions and regions would be a surprise
 * there.</p>
 */
public interface StatisticsMapper {

    @NotNull StatisticsEntity select();
}
