-- Adds the two-thirds majority used for Saudi extraordinary general assembly resolutions.
DO $$
DECLARE c TEXT;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'board_resolution'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%threshold%'
    LOOP
        EXECUTE format('ALTER TABLE board_resolution DROP CONSTRAINT %I', c);
    END LOOP;
END $$;
ALTER TABLE board_resolution
    ADD CONSTRAINT ck_resolution_threshold CHECK (threshold IN ('ORDINARY','TWO_THIRDS','SPECIAL'));
