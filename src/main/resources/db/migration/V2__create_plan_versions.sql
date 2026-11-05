-- Every save of a plan, so earlier texts can be looked at or restored.
create table plan_versions (
    id       bigint generated always as identity primary key,
    plan_id  uuid                     not null references plans (id) on delete cascade,
    -- The plans.version the save produced.
    version  bigint                   not null,
    name     varchar(200)             not null,
    content  text                     not null,
    valid    boolean                  not null,
    saved_at timestamp with time zone not null,
    unique (plan_id, version)
);
