alter table repo_sync_jobs
    add column error        text,
    add progress_percent real default 0.0,
    add status text;
