create schema if not exists auth;

create or replace function auth.set_updated_at()
returns trigger as $$
begin
    new.updated_at := current_timestamp;
    return new;
end;
$$ language plpgsql;

create table auth.users (
    id uuid not null,
    email text not null,
    encoded_password text not null,
    name text not null,
    provider text not null,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint pk_users primary key (id),
    constraint uq_users__email unique (email),
    constraint ck_users__provider check (provider in ('LOCAL', 'GOOGLE', 'GITHUB'))
);

create index ix_users__created_at on auth.users (created_at);

create trigger trg_users__set_updated_at
before update on auth.users
for each row
when (old.* is distinct from new.*)
execute function auth.set_updated_at();
