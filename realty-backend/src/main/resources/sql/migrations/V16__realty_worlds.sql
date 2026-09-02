CREATE TABLE IF NOT EXISTS RealtyWorld
(
    worldId   UUID         NOT NULL PRIMARY KEY,
    worldName VARCHAR(255) NOT NULL
);

CREATE INDEX idx_RealtyWorld_worldName ON RealtyWorld (worldName);
