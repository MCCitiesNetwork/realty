CREATE TABLE IF NOT EXISTS RealtySchematic
(
    realtyRegionId INT      NOT NULL PRIMARY KEY,
    data           LONGBLOB NOT NULL,
    capturedAt     DATETIME NOT NULL,
    capturedBy     UUID     NOT NULL
);
