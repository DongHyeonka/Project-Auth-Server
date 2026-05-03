alter table auth.users drop constraint ck_users__provider;

alter table auth.users
    add constraint ck_users__provider check (provider in ('LOCAL', 'KEYCLOAK', 'GOOGLE', 'GITHUB'));
