CREATE TABLE IF NOT EXISTS fitplan_user_profile (
    chat_id varchar(128) PRIMARY KEY,
    age varchar(32) NOT NULL DEFAULT '',
    goal varchar(512) NOT NULL DEFAULT '',
    experience varchar(256) NOT NULL DEFAULT '',
    weekly_days varchar(64) NOT NULL DEFAULT '',
    session_minutes varchar(64) NOT NULL DEFAULT '',
    equipment text NOT NULL DEFAULT '',
    health_notes text NOT NULL DEFAULT '',
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fitplan_training_log (
    id bigserial PRIMARY KEY,
    chat_id varchar(128) NOT NULL,
    log_date varchar(64) NOT NULL DEFAULT '',
    exercise text NOT NULL DEFAULT '',
    sets_and_reps text NOT NULL DEFAULT '',
    load_value varchar(128) NOT NULL DEFAULT '',
    rpe varchar(32) NOT NULL DEFAULT '',
    notes text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS fitplan_training_log_chat_idx
    ON fitplan_training_log(chat_id, id DESC);

CREATE TABLE IF NOT EXISTS fitplan_plan_summary (
    chat_id varchar(128) PRIMARY KEY,
    goal text NOT NULL DEFAULT '',
    weekly_schedule text NOT NULL DEFAULT '',
    progression_rule text NOT NULL DEFAULT '',
    updated_at timestamptz NOT NULL DEFAULT now()
);
