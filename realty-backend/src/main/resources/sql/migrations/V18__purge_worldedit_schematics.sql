-- Every capture before this version is a WorldEdit schematic: a complete copy of the
-- region, interior included, at its world position. The format that replaces it holds
-- none of that, and nothing restores a region from a capture, so the old rows have no
-- use left. They are removed rather than left to be overwritten one recapture at a time.
DELETE FROM RealtySchematic;
