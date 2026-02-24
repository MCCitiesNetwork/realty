package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;

public interface SqlSessionWrapper {

    @NotNull SqlSession session();

    @NotNull ContractMapper contractMapper();

    @NotNull LeaseContractMapper leaseContractMapper();

    @NotNull RealtyRegionMapper realtyRegionMapper();

    @NotNull SaleContractAuctionMapper saleContractAuctionMapper();

    @NotNull SaleContractMapper saleContractMapper();


}
