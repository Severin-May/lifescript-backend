create table users (
    id            uuid                     primary key,
    -- Stored lowercased, so an address is one account however it's typed.
    email         varchar(254)             not null unique,
    -- A BCrypt hash; the password itself is never stored.
    password_hash varchar(100)             not null,
    created_at    timestamp with time zone not null
);

-- Plans saved before accounts belonged to a placeholder owner, not a real user.
delete from plans where owner_id = 'local';

alter table plans
    alter column owner_id type uuid using owner_id::uuid,
    add constraint plans_owner_fk foreign key (owner_id) references users (id) on delete cascade;
