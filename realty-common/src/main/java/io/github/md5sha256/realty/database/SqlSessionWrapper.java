package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.SaleContractSanctionedAuctioneerMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

public interface SqlSessionWrapper extends Closeable {

    @NotNull SqlSession session();

    @NotNull ContractMapper contractMapper();

    @NotNull LeaseContractMapper leaseContractMapper();

    @NotNull RealtyRegionMapper realtyRegionMapper();

    @NotNull SaleContractAuctionMapper saleContractAuctionMapper();

    @NotNull SaleContractBidMapper saleContractBidMapper();

    @NotNull SaleContractBidPaymentMapper saleContractBidPaymentMapper();

    @NotNull SaleContractMapper saleContractMapper();

    @NotNull SaleContractOfferMapper saleContractOfferMapper();

    @NotNull SaleContractOfferPaymentMapper saleContractOfferPaymentMapper();

    @NotNull SaleContractSanctionedAuctioneerMapper saleContractSanctionedAuctioneerMapper();

    @Override
    void close();
}
