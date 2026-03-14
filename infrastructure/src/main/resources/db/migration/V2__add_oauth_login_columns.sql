alter table users alter column encoded_password drop not null;

alter table users add column provider_subject varchar(255);

alter table users add constraint uk_users_provider_subject unique (provider, provider_subject);
