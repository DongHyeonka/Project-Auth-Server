alter table auth.users alter column encoded_password drop not null;

alter table auth.users add column provider_subject text;

alter table auth.users
    add constraint uq_users__provider_provider_subject unique (provider, provider_subject);
