package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.mapper.ContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.LeaseHistoryMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdHistoryMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractOfferPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractBidPaymentMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractSanctionedAuctioneerMapper;
import org.apache.ibatis.session.SqlSession;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

public interface SqlSessionWrapper extends Closeable {

    @NotNull SqlSession session();

    @NotNull ContractMapper contractMapper();

    @NotNull LeaseContractMapper leaseContractMapper();

    @NotNull RealtyRegionMapper realtyRegionMapper();

    @NotNull FreeholdHistoryMapper freeholdHistoryMapper();

    @NotNull LeaseHistoryMapper leaseHistoryMapper();

    @NotNull FreeholdContractAuctionMapper freeholdContractAuctionMapper();

    @NotNull FreeholdContractBidMapper freeholdContractBidMapper();

    @NotNull FreeholdContractBidPaymentMapper freeholdContractBidPaymentMapper();

    @NotNull FreeholdContractMapper freeholdContractMapper();

    @NotNull FreeholdContractOfferMapper freeholdContractOfferMapper();

    @NotNull FreeholdContractOfferPaymentMapper freeholdContractOfferPaymentMapper();

    @NotNull FreeholdContractSanctionedAuctioneerMapper freeholdContractSanctionedAuctioneerMapper();

    @Override
    void close();
}
