
INSERT INTO organizations (id)
VALUES ('atlas'), ('beacon')
ON CONFLICT DO NOTHING;

INSERT INTO app_users (username, organization_id)
VALUES
    ('alice', 'atlas'),
    ('charlie', 'atlas'),
    ('bob', 'beacon'),
    ('admin-atlas', 'atlas'),
    ('admin-beacon', 'beacon')
ON CONFLICT (username)
DO UPDATE SET organization_id = EXCLUDED.organization_id;

INSERT INTO documents (id, organization_id, content)
VALUES
    ('atlas-plan', 'atlas', 'SYNTH-ATLAS-PLAN-001'),
    ('atlas-notes', 'atlas', 'SYNTH-ATLAS-NOTES-002'),
    ('beacon-plan', 'beacon', 'SYNTH-BEACON-PLAN-001')
ON CONFLICT (id)
DO UPDATE SET
    organization_id = EXCLUDED.organization_id,
    content = EXCLUDED.content;

DELETE FROM document_grants
WHERE document_id IN ('atlas-plan', 'atlas-notes', 'beacon-plan');

INSERT INTO document_grants (document_id, username)
VALUES
    ('atlas-plan', 'alice'),
    ('atlas-notes', 'alice'),
    ('beacon-plan', 'bob');
