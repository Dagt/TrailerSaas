CREATE TABLE users (
    id uuid PRIMARY KEY,
    firebase_uid varchar UNIQUE NOT NULL,
    email varchar NULL,
    display_name varchar NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

-- Validated ASCII identifier from DB_RUNTIME_USERNAME; quoted to preserve case.
-- Metadata is captured on first creation only; runtime requires no UPDATE/DELETE.
GRANT SELECT, INSERT ON TABLE users TO "${runtimeRole}";
