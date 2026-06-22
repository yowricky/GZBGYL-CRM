INSERT INTO permission (id, code, name, description) VALUES
('10000000-0000-0000-0000-000000000012', 'opportunity:read:company', 'Read company opportunities', NULL),
('10000000-0000-0000-0000-000000000013', 'financial:read:own', 'Read financial fields on own records', NULL),
('10000000-0000-0000-0000-000000000014', 'financial:read:department', 'Read financial fields in department', NULL),
('10000000-0000-0000-0000-000000000015', 'financial:read:company', 'Read company financial fields', NULL)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

DELETE FROM role_permission
WHERE role_id IN (
    SELECT id FROM role WHERE code IN (
        'SALES', 'SALES_MANAGER', 'PRESALES_TECH', 'PROJECT_MANAGER',
        'OPERATIONS_VIEWER', 'FINANCE_VIEWER', 'EXECUTIVE_VIEWER', 'SYSTEM_ADMIN'
    )
);

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id FROM (VALUES
('SALES', 'opportunity:read:own'), ('SALES', 'financial:read:own'),
('SALES', 'contract:read:authorized'), ('SALES', 'payment:read:authorized'),
('SALES_MANAGER', 'opportunity:read:department'), ('SALES_MANAGER', 'financial:read:department'),
('SALES_MANAGER', 'lead:assign:department'), ('SALES_MANAGER', 'performance:read:authorized'),
('SALES_MANAGER', 'contract:read:authorized'), ('SALES_MANAGER', 'payment:read:authorized'),
('PRESALES_TECH', 'opportunity:read:assigned'), ('PRESALES_TECH', 'opportunity:technical:update'),
('PROJECT_MANAGER', 'opportunity:read:assigned'), ('PROJECT_MANAGER', 'project:read:assigned'),
('OPERATIONS_VIEWER', 'project:read:assigned'), ('OPERATIONS_VIEWER', 'financial:read:department'),
('FINANCE_VIEWER', 'performance:read:authorized'), ('FINANCE_VIEWER', 'contract:read:authorized'),
('FINANCE_VIEWER', 'payment:read:authorized'), ('FINANCE_VIEWER', 'financial:read:department'),
('EXECUTIVE_VIEWER', 'opportunity:read:company'), ('EXECUTIVE_VIEWER', 'financial:read:company'),
('EXECUTIVE_VIEWER', 'performance:read:company'), ('EXECUTIVE_VIEWER', 'contract:read:authorized'),
('EXECUTIVE_VIEWER', 'payment:read:authorized'),
('SYSTEM_ADMIN', 'system:admin')
) AS mapping(role_code, permission_code)
JOIN role ON role.code = mapping.role_code
JOIN permission ON permission.code = mapping.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
