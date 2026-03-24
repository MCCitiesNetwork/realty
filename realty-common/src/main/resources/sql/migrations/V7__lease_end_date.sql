ALTER TABLE LeaseContract
    ADD COLUMN endDate DATETIME;

UPDATE LeaseContract
SET endDate = startDate + INTERVAL durationSeconds SECOND
WHERE tenant IS NOT NULL;
