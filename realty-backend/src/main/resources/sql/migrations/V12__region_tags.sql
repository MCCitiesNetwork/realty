CREATE TABLE IF NOT EXISTS RegionTag
(
    tagId              VARCHAR(255) NOT NULL,
    worldGuardRegionId VARCHAR(255) NOT NULL,
    PRIMARY KEY (tagId, worldGuardRegionId)
);

CREATE INDEX idx_region_tag_tag ON RegionTag (tagId);
CREATE INDEX idx_region_tag_region ON RegionTag (worldGuardRegionId);
