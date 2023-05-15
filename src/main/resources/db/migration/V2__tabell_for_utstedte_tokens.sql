CREATE TABLE feed_consumer(
    id              UUID        PRIMARY KEY,
    identifikator   TEXT        NOT NULL,
    email           TEXT        NOT NULL,
    telefon         TEXT        NOT NULL,
    kontaktperson   TEXT        NOT NULL,
    opprettet       TIMESTAMP   NOT NULL
);

CREATE TABLE token(
    id              UUID        PRIMARY KEY,
    consumer_id     UUID        NOT NULL,
    jwt             TEXT        NOT NULL,
    issued_at       TIMESTAMP   NOT NULL,
    invalidated     BOOLEAN     DEFAULT FALSE,
    invalidated_at  TIMESTAMP
);
