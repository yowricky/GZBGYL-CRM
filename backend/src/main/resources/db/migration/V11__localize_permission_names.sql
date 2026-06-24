UPDATE permission
SET
    name = CASE code
        WHEN 'system:admin' THEN '系统管理'
        WHEN 'opportunity:read:own' THEN '查看本人商机'
        WHEN 'opportunity:read:department' THEN '查看部门商机'
        WHEN 'opportunity:read:assigned' THEN '查看分配商机'
        WHEN 'opportunity:technical:update' THEN '维护商机技术信息'
        WHEN 'opportunity:read:company' THEN '查看公司商机'
        WHEN 'lead:assign:department' THEN '分配部门线索'
        WHEN 'project:read:assigned' THEN '查看分配项目'
        WHEN 'performance:read:authorized' THEN '查看授权业绩'
        WHEN 'performance:read:company' THEN '查看公司业绩'
        WHEN 'contract:read:authorized' THEN '查看授权合同'
        WHEN 'payment:read:authorized' THEN '查看授权回款'
        WHEN 'financial:read:own' THEN '查看本人财务字段'
        WHEN 'financial:read:department' THEN '查看部门财务字段'
        WHEN 'financial:read:company' THEN '查看公司财务字段'
        WHEN 'business:lead:create' THEN '新建线索'
        WHEN 'business:lead:read' THEN '查看线索'
        WHEN 'business:lead:update' THEN '编辑线索'
        WHEN 'business:lead:delete' THEN '删除线索'
        WHEN 'business:account:create' THEN '新建客户'
        WHEN 'business:account:read' THEN '查看客户'
        WHEN 'business:account:update' THEN '编辑客户'
        WHEN 'business:account:delete' THEN '删除客户'
        WHEN 'business:contact:create' THEN '新建联系人'
        WHEN 'business:contact:read' THEN '查看联系人'
        WHEN 'business:contact:update' THEN '编辑联系人'
        WHEN 'business:contact:delete' THEN '删除联系人'
        WHEN 'business:opportunity:create' THEN '新建商机'
        WHEN 'business:opportunity:read' THEN '查看商机'
        WHEN 'business:opportunity:update' THEN '编辑商机'
        WHEN 'business:opportunity:delete' THEN '删除商机'
        WHEN 'business:quote:create' THEN '新建报价'
        WHEN 'business:quote:read' THEN '查看报价'
        WHEN 'business:quote:update' THEN '编辑报价'
        WHEN 'business:quote:delete' THEN '删除报价'
        WHEN 'business:contract:create' THEN '新建合同'
        WHEN 'business:contract:read' THEN '查看合同'
        WHEN 'business:contract:update' THEN '编辑合同'
        WHEN 'business:contract:delete' THEN '删除合同'
        ELSE name
    END,
    description = CASE code
        WHEN 'system:admin' THEN '拥有系统管理功能的全部权限'
        WHEN 'opportunity:read:own' THEN '可查看本人负责的商机'
        WHEN 'opportunity:read:department' THEN '可查看所在部门及下级部门商机'
        WHEN 'opportunity:read:assigned' THEN '可查看分配给本人的商机'
        WHEN 'opportunity:technical:update' THEN '可维护商机中的技术方案信息'
        WHEN 'opportunity:read:company' THEN '可查看公司范围内商机'
        WHEN 'lead:assign:department' THEN '可分配部门范围内线索'
        WHEN 'project:read:assigned' THEN '可查看分配给本人的项目'
        WHEN 'performance:read:authorized' THEN '可查看授权范围内业绩'
        WHEN 'performance:read:company' THEN '可查看公司范围内业绩'
        WHEN 'contract:read:authorized' THEN '可查看授权范围内合同'
        WHEN 'payment:read:authorized' THEN '可查看授权范围内回款'
        WHEN 'financial:read:own' THEN '可查看本人负责记录的财务字段'
        WHEN 'financial:read:department' THEN '可查看部门范围内财务字段'
        WHEN 'financial:read:company' THEN '可查看公司范围内财务字段'
        ELSE description
    END
WHERE code IN (
    'system:admin',
    'opportunity:read:own',
    'opportunity:read:department',
    'opportunity:read:assigned',
    'opportunity:technical:update',
    'opportunity:read:company',
    'lead:assign:department',
    'project:read:assigned',
    'performance:read:authorized',
    'performance:read:company',
    'contract:read:authorized',
    'payment:read:authorized',
    'financial:read:own',
    'financial:read:department',
    'financial:read:company',
    'business:lead:create',
    'business:lead:read',
    'business:lead:update',
    'business:lead:delete',
    'business:account:create',
    'business:account:read',
    'business:account:update',
    'business:account:delete',
    'business:contact:create',
    'business:contact:read',
    'business:contact:update',
    'business:contact:delete',
    'business:opportunity:create',
    'business:opportunity:read',
    'business:opportunity:update',
    'business:opportunity:delete',
    'business:quote:create',
    'business:quote:read',
    'business:quote:update',
    'business:quote:delete',
    'business:contract:create',
    'business:contract:read',
    'business:contract:update',
    'business:contract:delete'
);
