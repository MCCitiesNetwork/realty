-- A tag now names its region by name and world, as RealtyRegion does. Before this a tag named
-- only the region's name, so two regions with one name in different worlds shared their tags.

ALTER TABLE RegionTag DROP PRIMARY KEY;
ALTER TABLE RegionTag ADD COLUMN worldId UUID NULL;
-- One row for each registered region that has the name.
INSERT INTO RegionTag (tagId, worldGuardRegionId, worldId)
SELECT rt.tagId, rt.worldGuardRegionId, rr.worldId
FROM RegionTag rt
INNER JOIN RealtyRegion rr ON rr.worldGuardRegionId = rt.worldGuardRegionId;
-- The original rows go, and with them the tags on a name that no registered region has: a region
-- delete left those behind, or the region was never registered, and no world can be given to them.
DELETE FROM RegionTag WHERE worldId IS NULL;
ALTER TABLE RegionTag MODIFY worldId UUID NOT NULL;
ALTER TABLE RegionTag ADD PRIMARY KEY (tagId, worldGuardRegionId, worldId);
DROP INDEX idx_region_tag_region ON RegionTag;
CREATE INDEX idx_region_tag_region ON RegionTag (worldGuardRegionId, worldId);
