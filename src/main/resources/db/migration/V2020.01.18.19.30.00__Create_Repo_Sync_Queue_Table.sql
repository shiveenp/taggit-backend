create table repo_sync_jobs
(
    id        uuid PRIMARY KEY,
    user_id   uuid not null,
    completed boolean default false,
    created_at timestamptz default now()
);

ALTER TABLE repo_sync_jobs
    ADD CONSTRAINT user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) MATCH FULL;
