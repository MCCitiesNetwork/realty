ALTER TABLE LeaseContract
    ADD COLUMN endDate DATETIME;

UPDATE LeaseContract
SET endDate = startDate + INTERVAL durationSeconds SECOND;

ALTER TABLE LeaseContract
    MODIFY COLUMN endDate DATETIME NOT NULL;
