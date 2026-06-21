INSERT INTO permission (id, code, name, description) VALUES
('10000000-0000-0000-0000-000000000001', 'system:admin', 'System administration', 'Full system administration'),
('10000000-0000-0000-0000-000000000002', 'opportunity:read:own', 'Read own opportunities', NULL),
('10000000-0000-0000-0000-000000000003', 'opportunity:read:department', 'Read department opportunities', NULL),
('10000000-0000-0000-0000-000000000004', 'opportunity:read:assigned', 'Read assigned opportunities', NULL),
('10000000-0000-0000-0000-000000000005', 'opportunity:technical:update', 'Update opportunity technical details', NULL),
('10000000-0000-0000-0000-000000000006', 'lead:assign:department', 'Assign department leads', NULL),
('10000000-0000-0000-0000-000000000007', 'project:read:assigned', 'Read assigned projects', NULL),
('10000000-0000-0000-0000-000000000008', 'performance:read:authorized', 'Read authorized performance', NULL),
('10000000-0000-0000-0000-000000000009', 'performance:read:company', 'Read company performance', NULL),
('10000000-0000-0000-0000-000000000010', 'contract:read:authorized', 'Read authorized contracts', NULL),
('10000000-0000-0000-0000-000000000011', 'payment:read:authorized', 'Read authorized payments', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role (id, code, name, system_role) VALUES
('20000000-0000-0000-0000-000000000001', 'SALES', 'Sales', TRUE),
('20000000-0000-0000-0000-000000000002', 'SALES_MANAGER', 'Sales Manager', TRUE),
('20000000-0000-0000-0000-000000000003', 'PRESALES_TECH', 'Presales Technical', TRUE),
('20000000-0000-0000-0000-000000000004', 'PROJECT_MANAGER', 'Project Manager', TRUE),
('20000000-0000-0000-0000-000000000005', 'OPERATIONS_VIEWER', 'Operations Viewer', TRUE),
('20000000-0000-0000-0000-000000000006', 'FINANCE_VIEWER', 'Finance Viewer', TRUE),
('20000000-0000-0000-0000-000000000007', 'EXECUTIVE_VIEWER', 'Executive Viewer', TRUE),
('20000000-0000-0000-0000-000000000008', 'SYSTEM_ADMIN', 'System Administrator', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM (VALUES
('SALES', 'opportunity:read:own'), ('SALES', 'opportunity:read:assigned'),
('SALES', 'contract:read:authorized'), ('SALES', 'payment:read:authorized'),
('SALES_MANAGER', 'opportunity:read:department'), ('SALES_MANAGER', 'lead:assign:department'),
('SALES_MANAGER', 'performance:read:authorized'), ('SALES_MANAGER', 'contract:read:authorized'),
('SALES_MANAGER', 'payment:read:authorized'),
('PRESALES_TECH', 'opportunity:read:assigned'), ('PRESALES_TECH', 'opportunity:technical:update'),
('PROJECT_MANAGER', 'opportunity:read:assigned'), ('PROJECT_MANAGER', 'project:read:assigned'),
('OPERATIONS_VIEWER', 'opportunity:read:assigned'), ('OPERATIONS_VIEWER', 'project:read:assigned'),
('FINANCE_VIEWER', 'performance:read:authorized'), ('FINANCE_VIEWER', 'contract:read:authorized'),
('FINANCE_VIEWER', 'payment:read:authorized'),
('EXECUTIVE_VIEWER', 'performance:read:company'), ('EXECUTIVE_VIEWER', 'contract:read:authorized'),
('EXECUTIVE_VIEWER', 'payment:read:authorized'),
('SYSTEM_ADMIN', 'system:admin')
) AS mapping(role_code, permission_code)
JOIN role r ON r.code = mapping.role_code
JOIN permission p ON p.code = mapping.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
