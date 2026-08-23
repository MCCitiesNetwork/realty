-- Repair leaseholds left with a non-null extension cap but a null used-extension count.
-- applyModificationTerms used to raise maxExtensions on a previously uncapped contract without
-- seeding currentMaxExtensions, and MariaDB does not evaluate CHECK constraints for the
-- multi-table UPDATE that wrote it, so chk_extensions never rejected the row. Renewing such a
-- contract then failed unboxing currentMaxExtensions. Treat the count as zero: no extension has
-- been consumed under the new cap.
UPDATE LeaseholdContract
SET currentMaxExtensions = 0
WHERE maxExtensions IS NOT NULL
  AND currentMaxExtensions IS NULL;
