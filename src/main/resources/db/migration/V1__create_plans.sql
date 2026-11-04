-- A saved plan is its LifeScript text; the compiled Plan and its schedule are computed again when needed.
create table plans (
    id         uuid                     primary key,
    owner_id   varchar(100)             not null,
    name       varchar(200)             not null,
    content    text                     not null,
    -- Whether the text compiled without errors when it was saved; drafts may be saved too.
    valid      boolean                  not null,
    -- Optimistic locking: a save based on an older version is refused.
    version    bigint                   not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index plans_owner_id on plans (owner_id);
