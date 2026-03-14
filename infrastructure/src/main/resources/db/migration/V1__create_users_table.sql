create table users (
    id uuid not null,
    email varchar(320) not null,
    encoded_password varchar(255) not null,
    name varchar(20) not null,
    provider varchar(20) not null,
    created_at timestamp with time zone not null,
    constraint pk_users primary key (id),
    constraint uk_users_email unique (email)
);

create index idx_users_created_at on users (created_at);
