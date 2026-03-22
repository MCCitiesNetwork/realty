CREATE TABLE IF NOT EXISTS RealtySign
(
    worldId        UUID NOT NULL,
    blockX         INT  NOT NULL,
    blockY         INT  NOT NULL,
    blockZ         INT  NOT NULL,
    realtyRegionId INT  NOT NULL,
    chunkX         INT  NOT NULL,
    chunkZ         INT  NOT NULL,
    PRIMARY KEY (worldId, blockX, blockY, blockZ)
);

ALTER TABLE RealtySign
    ADD (
        CONSTRAINT RealtySign_RealtyRegion_fk
            FOREIGN KEY (realtyRegionId) REFERENCES RealtyRegion (realtyRegionId) ON DELETE CASCADE
        );

CREATE INDEX idx_realty_sign_chunk ON RealtySign (worldId, chunkX, chunkZ);
CREATE INDEX idx_realty_sign_region ON RealtySign (realtyRegionId);
