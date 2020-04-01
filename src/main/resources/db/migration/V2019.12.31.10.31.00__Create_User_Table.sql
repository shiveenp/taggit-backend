create table users (
    id UUID PRIMARY KEY,
    user_name text not null,
    email text not null,
    password text not null,
    github_user_name text not null,
    github_user_id bigint not null unique,
    access_token text not null,
    token_refreshed_at timestamptz null,
    last_login_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
