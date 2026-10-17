-- Copied from coffer-docs data-model.md §4.3 (Sprint 3, S3-DB01)
CREATE SEQUENCE sync_version AS BIGINT START WITH 1 INCREMENT BY 1;

-- Asigna un número de versión global y monotónico en cada escritura.
-- Es el cursor de GET /api/v1/sync/pull?since={version}.
CREATE FUNCTION set_sync_version() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.version := nextval('sync_version');
    RETURN NEW;
END;
$$;
