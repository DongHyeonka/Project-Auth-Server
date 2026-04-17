alter table auth.users
    add constraint ck_users__local_password_required
        check (
            (provider = 'LOCAL' and encoded_password is not null and provider_subject is null)
            or (provider <> 'LOCAL')
        );

alter table auth.users
    add constraint ck_users__social_subject_required
        check (
            (provider <> 'LOCAL' and provider_subject is not null and encoded_password is null)
            or (provider = 'LOCAL')
        );
