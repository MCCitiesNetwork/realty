UPDATE LeaseholdContract
SET startDate = NULL,
    endDate = NULL
WHERE tenantId IS NULL
  AND (startDate IS NOT NULL OR endDate IS NOT NULL);
