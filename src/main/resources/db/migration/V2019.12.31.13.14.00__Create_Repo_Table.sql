create table repo
(
    id                 uuid PRIMARY KEY,
    user_id            uuid    not null,
    repo_id            bigint not null,
    repo_name          text   not null,
    github_link        text   not null,
    github_description text,
    star_count         int    not null,
    owner_avatar_url   text,
    metadata               jsonb
);

ALTER TABLE repo
    ADD CONSTRAINT user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) MATCH FULL;
