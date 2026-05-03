alter table auth.users drop constraint if exists ck_users__local_password_required;
alter table auth.users drop constraint if exists ck_users__social_subject_required;
alter table auth.users drop constraint if exists ck_users__provider;

alter table auth.users drop column if exists encoded_password;

alter table auth.users alter column provider_subject set not null;

alter table auth.users
    add constraint ck_users__provider check (provider = 'KEYCLOAK');
