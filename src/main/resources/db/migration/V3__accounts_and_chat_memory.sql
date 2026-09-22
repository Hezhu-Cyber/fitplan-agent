CREATE TABLE IF NOT EXISTS fitplan_app_user (
    id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    email varchar(320) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(80) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS fitplan_app_user_email_unique
    ON fitplan_app_user(lower(email));

CREATE TABLE IF NOT EXISTS fitplan_auth_session (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES fitplan_app_user(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS fitplan_auth_session_user_idx
    ON fitplan_auth_session(user_id);
CREATE INDEX IF NOT EXISTS fitplan_auth_session_expiry_idx
    ON fitplan_auth_session(expires_at);

CREATE TABLE IF NOT EXISTS fitplan_chat_message (
    id bigserial PRIMARY KEY,
    conversation_id varchar(256) NOT NULL,
    message_type varchar(32) NOT NULL,
    content text NOT NULL DEFAULT '',
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    tool_payload jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS fitplan_chat_message_conversation_idx
    ON fitplan_chat_message(conversation_id, id);
CREATE INDEX IF NOT EXISTS fitplan_chat_message_expiry_idx
    ON fitplan_chat_message(expires_at);

ALTER TABLE fitplan_user_profile ADD COLUMN IF NOT EXISTS owner_id uuid;
ALTER TABLE fitplan_plan_summary ADD COLUMN IF NOT EXISTS owner_id uuid;
ALTER TABLE fitplan_training_log ADD COLUMN IF NOT EXISTS owner_id uuid;
ALTER TABLE fitplan_training_log ADD COLUMN IF NOT EXISTS idempotency_key varchar(128);

UPDATE fitplan_user_profile
SET owner_id = '00000000-0000-0000-0000-000000000001'
WHERE owner_id IS NULL;
UPDATE fitplan_plan_summary
SET owner_id = '00000000-0000-0000-0000-000000000001'
WHERE owner_id IS NULL;
UPDATE fitplan_training_log
SET owner_id = '00000000-0000-0000-0000-000000000001'
WHERE owner_id IS NULL;

ALTER TABLE fitplan_user_profile ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE fitplan_plan_summary ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE fitplan_training_log ALTER COLUMN owner_id SET NOT NULL;

ALTER TABLE fitplan_user_profile DROP CONSTRAINT IF EXISTS fitplan_user_profile_pkey;
ALTER TABLE fitplan_user_profile
    ADD CONSTRAINT fitplan_user_profile_pkey PRIMARY KEY (owner_id, chat_id);
ALTER TABLE fitplan_plan_summary DROP CONSTRAINT IF EXISTS fitplan_plan_summary_pkey;
ALTER TABLE fitplan_plan_summary
    ADD CONSTRAINT fitplan_plan_summary_pkey PRIMARY KEY (owner_id, chat_id);

CREATE INDEX IF NOT EXISTS fitplan_training_log_owner_idx
    ON fitplan_training_log(owner_id, chat_id, id DESC);
CREATE UNIQUE INDEX IF NOT EXISTS fitplan_training_log_idempotency_unique
    ON fitplan_training_log(owner_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
