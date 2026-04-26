CREATE TABLE transactions (
    device_id    UUID PRIMARY KEY,
    server_id    TEXT                     NULL,
    title        VARCHAR(100)             NOT NULL,
    value        NUMERIC(19, 4)           NOT NULL,
    type         BOOLEAN                  NOT NULL,
    description  TEXT                     NULL,
    date         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    synchronized BOOLEAN                  NOT NULL
);