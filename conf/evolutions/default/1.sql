-- !Ups
-- auto-generated definition
create table users
(
    id       integer not null
        constraint users_pk
            primary key autoincrement,
    username string  not null,
    password string  not null,
    isAdmin  boolean default false,
    handle   string  not null
);

create unique index users_handle_uindex
    on users (handle);

create unique index users_id_uindex
    on users (id);

create unique index users_username_uindex
    on users (username);



create table keys
(
    id             integer not null
        constraint keys_pk
            primary key autoincrement,
    user           integer not null,
    keyId          string  not null,
    credentialData string not null,
    statement      string not null,
    counter        long default 0 not null
);

create index keys_keyId_index
    on keys (keyId);

create unique index keys_keyId_uindex
    on keys (keyId);


-- !Downs

DROP TABLE users;
DROP TABLE keys;
