
CREATE TABLE organizations (
    id TEXT PRIMARY KEY
);

CREATE TABLE app_users (
    username TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id)
);

CREATE TABLE documents (
    id TEXT PRIMARY KEY,
    organization_id TEXT NOT NULL REFERENCES organizations(id),
    content TEXT NOT NULL
);

CREATE TABLE document_grants (
    document_id TEXT NOT NULL REFERENCES documents(id),
    username TEXT NOT NULL REFERENCES app_users(username),
    PRIMARY KEY (document_id, username)
);

INSERT INTO organizations VALUES
    ('atlas'),
    ('beacon');

INSERT INTO app_users VALUES
    ('alice', 'atlas'),
    ('charlie', 'atlas'),
    ('bob', 'beacon'),
    ('admin-atlas', 'atlas'),
    ('admin-beacon', 'beacon');

INSERT INTO documents VALUES
    ('atlas-plan', 'atlas', 'SYNTH-ATLAS-PLAN-001'),
    ('atlas-notes', 'atlas', 'SYNTH-ATLAS-NOTES-002'),
    ('beacon-plan', 'beacon', 'SYNTH-BEACON-PLAN-001');

INSERT INTO document_grants VALUES
    ('atlas-plan', 'alice'),
    ('atlas-notes', 'alice'),
    ('beacon-plan', 'bob');
