-- Seed business module permissions.
-- The identity schema uses singular table names: permission, role, role_permission.

INSERT INTO permission (code, name, description)
VALUES
    ('business:lead:create', 'Create lead', 'Create sales leads'),
    ('business:lead:read', 'Read lead', 'Read sales leads'),
    ('business:lead:update', 'Update lead', 'Update sales leads'),
    ('business:lead:delete', 'Delete lead', 'Delete sales leads'),
    ('business:account:create', 'Create account', 'Create customer accounts'),
    ('business:account:read', 'Read account', 'Read customer accounts'),
    ('business:account:update', 'Update account', 'Update customer accounts'),
    ('business:account:delete', 'Delete account', 'Delete customer accounts'),
    ('business:contact:create', 'Create contact', 'Create customer contacts'),
    ('business:contact:read', 'Read contact', 'Read customer contacts'),
    ('business:contact:update', 'Update contact', 'Update customer contacts'),
    ('business:contact:delete', 'Delete contact', 'Delete customer contacts'),
    ('business:opportunity:create', 'Create opportunity', 'Create opportunities'),
    ('business:opportunity:read', 'Read opportunity', 'Read opportunities'),
    ('business:opportunity:update', 'Update opportunity', 'Update opportunities'),
    ('business:opportunity:delete', 'Delete opportunity', 'Delete opportunities'),
    ('business:quote:create', 'Create quote', 'Create quotes'),
    ('business:quote:read', 'Read quote', 'Read quotes'),
    ('business:quote:update', 'Update quote', 'Update quotes'),
    ('business:quote:delete', 'Delete quote', 'Delete quotes'),
    ('business:contract:create', 'Create contract', 'Create contracts'),
    ('business:contract:read', 'Read contract', 'Read contracts'),
    ('business:contract:update', 'Update contract', 'Update contracts'),
    ('business:contract:delete', 'Delete contract', 'Delete contracts')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.code IN ('SALES', 'SYSTEM_ADMIN')
  AND p.code IN (
      'business:lead:create', 'business:lead:read', 'business:lead:update', 'business:lead:delete',
      'business:account:create', 'business:account:read', 'business:account:update', 'business:account:delete',
      'business:contact:create', 'business:contact:read', 'business:contact:update', 'business:contact:delete',
      'business:opportunity:create', 'business:opportunity:read', 'business:opportunity:update', 'business:opportunity:delete',
      'business:quote:create', 'business:quote:read', 'business:quote:update', 'business:quote:delete',
      'business:contract:create', 'business:contract:read', 'business:contract:update', 'business:contract:delete'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
