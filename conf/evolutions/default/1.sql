-- !Ups
create table keys
(
    id integer not null
        constraint keys_pk
            primary key autoincrement,
    user integer not null,
    keyId string not null,
    credentialData string not null,
    statement string not null,
    counter long default 0 not null
);

create index keys_keyId_index
    on keys (keyId);

create unique index keys_keyId_uindex
    on keys (keyId);

create table roles
(
    id integer not null
        constraint roles_pk
            primary key autoincrement,
    role string not null
);

create unique index roles_role_uindex
    on roles (role);

create table rules
(
    id integer not null
        constraint rules_pk
            primary key autoincrement,
    name string not null,
    protocol_pattern string,
    host_pattern string,
    path_pattern string,
    is_public boolean default false not null,
    timeout int default 0
);

create table rules_x_role
(
    id integer not null
        constraint rules_x_role_pk
            primary key autoincrement,
    rule int not null
        references rules,
    role int not null
        references roles
);

create unique index rules_x_role_rule_role_uindex
    on rules_x_role (rule, role);

create table users
(
    id integer not null
        constraint users_pk
            primary key autoincrement,
    username string not null,
    password string not null,
    isAdmin boolean default false,
    handle string not null,
    duo_enabled boolean default false not null,
    totp_secret string
);

create unique index users_handle_uindex
    on users (handle);

create unique index users_id_uindex
    on users (id);

create unique index users_username_uindex
    on users (username);

create table users_x_role
(
    id integer not null
        constraint UsersXRole_pk
            primary key autoincrement,
    user integer not null
        references users,
    role int not null
        references roles
);

create unique index users_x_role_role_user_uindex
    on users_x_role (role, user);

-- !Downs

DROP TABLE users;
DROP TABLE keys;
DROP TABLE roles;
DROP TABLE users_x_role;
DROP TABLE rules_x_role;
DROP TABLE rules;
