# CRM 业务数据模型定义

> 基于 2026-06-24 讨论结果定稿
> 交付物：表单格式、属性定义、实体关联关系

---

## 基础约定

- 所有业务实体继承 BaseEntity（id/uuid, version, createdAt/By, updatedAt/By）
- 所有变更自动记录 audit_log
- 附件通过 `attachment` 表的 `owner_type + owner_id` 多态关联任意实体
- 主键统一 UUID，外键统一 UUID → 父表

---

## 实体关系总图

```
Lead ──→ Account ──→ Contact
 │         │
 │         ├── Opportunity ──→ Quote
 │         │       │
 │         │       └── Contract ──→ Payment
 │         └───────┘
 │                   
 └── Activity (多态关联)

 所有实体 ←── AuditLog（自动记录）
 所有实体 ←── Attachment（通过 owner_type + owner_id）
```

---

## 一、线索（Lead）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| source | VARCHAR(50) | ✅ | | 下拉单选 | 来源：伙伴推荐、销售自拓、市场活动、官网 |
| company_name | VARCHAR(500) | ✅ | | 文本框 | 客户公司名 |
| contact_person | VARCHAR(200) | | | 文本框 | 联系人姓名 |
| contact_phone | VARCHAR(50) | | | 文本框 | 联系电话 |
| contact_email | VARCHAR(200) | | | 文本框 | 联系邮箱 |
| budget_range | VARCHAR(100) | | | 文本框 | 预算范围 |
| requirement_desc | TEXT | | | 多行文本 | 需求描述 |
| expected_amount | DECIMAL(18,2) | | 0 | 数字 | 预计金额 |
| stage | VARCHAR(50) | ✅ | '待确认需求' | 下拉单选 | 阶段：待确认需求、初步沟通、已转客户、已转商机、已关闭 |
| status | VARCHAR(50) | | | 下拉单选 | 状态：高意向、待跟进、跟进中、已转化、已关闭 |
| owner_id | UUID → app_user | | | 人员选择 | 负责人 |
| closed_at | TIMESTAMPTZ | | | 日期 | 关闭时间 |
| close_reason | VARCHAR(500) | | | 多行文本 | 关闭原因 |

### 关联关系

- Lead → Account（已转客户时关联）
- Lead → Opportunity（已转商机时关联）
- Lead → Attachment（相关附件）

### 业务规则

- 转化后自动创建 Account 或 Opportunity
- 关闭时必须填写 close_reason

---

## 二、客户（Account）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| name | VARCHAR(500) | ✅ | | 文本框 | 客户名称 |
| type | VARCHAR(50) | ✅ | '一般客户' | 下拉单选 | 类型：重点客户、集团客户、潜在客户、一般客户 |
| industry | VARCHAR(200) | | | 文本框 | 行业 |
| region | VARCHAR(200) | | | 文本框 | 区域 |
| credit_code | VARCHAR(50) | | | 文本框 | 统一社会信用代码 |
| contact_address | TEXT | | | 多行文本 | 地址 |
| website | VARCHAR(500) | | | 文本框 | 网址 |
| annual_revenue | DECIMAL(18,2) | | | 数字 | 年收入 |
| employee_count | INTEGER | | | 数字 | 员工规模 |
| stage | VARCHAR(50) | ✅ | '跟进中' | 下拉单选 | 阶段：潜在、跟进中、合作中、沉默、已流失 |
| status | VARCHAR(50) | | '活跃' | 下拉单选 | 状态：活跃、跟进中、沉默、流失 |
| owner_id | UUID → app_user | | | 人员选择 | 客户负责人 |
| source_lead_id | UUID → Lead | | | 只读 | 来源线索（转客户时自动带入）|

### 关联关系

- Account → Contact（一对多）
- Account → Opportunity（一对多）
- Account → Quote（一对多）
- Account → Contract（一对多）
- Account → Attachment

---

## 三、联系人（Contact）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| account_id | UUID → Account | ✅ | | 客户选择 | 所属客户 |
| name | VARCHAR(200) | ✅ | | 文本框 | 联系人姓名 |
| role | VARCHAR(200) | | | 下拉单选 | 角色：技术、采购、财务、决策者、其他 |
| title | VARCHAR(200) | | | 文本框 | 职位 |
| phone | VARCHAR(50) | | | 文本框 | 电话 |
| email | VARCHAR(200) | | | 文本框 | 邮箱 |
| is_primary | BOOLEAN | | false | 开关 | 是否主要负责人 |
| notes | TEXT | | | 多行文本 | 备注 |

### 关联关系

- Contact → Account（多对一）
- Contact → Opportunity（作为主要联系人）

### 业务规则

- 一个客户有且仅有一个 `is_primary=true` 的联系人

---

## 四、商机（Opportunity）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| account_id | UUID → Account | ✅ | | 客户选择 | 关联客户 |
| contact_id | UUID → Contact | | | 联系人选择 | 主要联系人 |
| name | VARCHAR(500) | ✅ | | 文本框 | 商机名称 |
| description | TEXT | | | 多行文本 | 需求描述 |
| expected_amount | DECIMAL(18,2) | | 0 | 数字 | 预计金额 |
| probability | INTEGER | | 自动 | 只读 | 赢率%，根据阶段自动计算 |
| stage | VARCHAR(50) | ✅ | '初步接触' | 下拉单选 | 阶段：初步接触、方案交流、立项/预算、投标/报价、商务谈判、赢单、输单 |
| expected_close_date | DATE | | | 日期 | 预计关闭日期 |
| competitor | TEXT | | | 多行文本 | 竞争对手 |
| win_reason | VARCHAR(1000) | | | 多行文本 | 赢单原因 |
| lose_reason | VARCHAR(1000) | | | 多行文本 | 输单原因 |
| source_lead_id | UUID → Lead | | | 只读 | 来源线索 |
| owner_id | UUID → app_user | | | 人员选择 | 负责人 |

### 阶段赢率映射

| 阶段 | 赢率 |
|------|------|
| 初步接触 | 0% |
| 方案交流 | 40% |
| 立项/预算 | 50% |
| 投标/报价 | 60% |
| 商务谈判 | 85% |
| 赢单 | 100% |
| 输单 | 0% |

### 关联关系

- Opportunity → Account（多对一）
- Opportunity → Contact（多对一，可空）
- Opportunity → Lead（多对一，可空）
- Opportunity → Quote（一对多）
- Opportunity → Contract（一对多）
- Opportunity → Attachment

### 业务规则

- `probability` 由 `stage` 自动计算，不可手动修改
- 赢单/输单时必须填写对应原因
- 商机关闭后不可再新建报价/合同

---

## 五、报价（Quote）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| opportunity_id | UUID → Opportunity | ✅ | | 商机选择 | 关联商机 |
| account_id | UUID → Account | ✅ | 自动带出 | 只读 | 关联客户（从商机自动带出）|
| name | VARCHAR(500) | ✅ | | 文本框 | 报价名称/版本 |
| total_amount | DECIMAL(18,2) | | 0 | 数字 | 总金额 |
| delivery_cycle | VARCHAR(200) | | | 文本框 | 交付周期 |
| payment_terms | TEXT | | | 多行文本 | 付款条件 |
| stage | VARCHAR(50) | ✅ | '草稿' | 下拉单选 | 阶段：草稿、审批中、客户确认、已确认、已转合同、已过期 |
| status | VARCHAR(50) | | | 下拉单选 | 状态：待提交、审批中、待确认、已确认、已转合同、已过期 |
| approved_at | TIMESTAMPTZ | | | 日期（只读）| 审批时间 |
| approved_by | UUID → app_user | | | 人员（只读）| 审批人 |
| confirmed_at | TIMESTAMPTZ | | | 日期（只读）| 客户确认时间 |
| owner_id | UUID → app_user | | | 人员选择 | 负责人 |

### 报价明细

**不建表实现。** 报价明细通过已有附件系统上传 Excel 文件：

```
报价页面 → 附件上传（Excel）→ attachment 表
                              owner_type = 'quote'
                              owner_id = quote.id
```

### 关联关系

- Quote → Opportunity（多对一）
- Quote → Account（多对一）
- Quote → Contract（一对一，已转合同后关联）
- Quote → Attachment（报价明细 Excel）

### 业务规则

- 上传的 Excel 作为报价明细的唯一数据源
- 转合同后 `stage` 更新为"已转合同"，关联 Contract ID

---

## 六、合同（Contract）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| opportunity_id | UUID → Opportunity | | | 商机选择 | 关联商机 |
| quote_id | UUID → Quote | | | 报价选择 | 来源报价 |
| account_id | UUID → Account | ✅ | 自动带出 | 只读 | 客户 |
| contract_no | VARCHAR(100) | ✅ | 自动生成 | 文本框 | 合同编号，唯一 |
| name | VARCHAR(500) | ✅ | | 文本框 | 合同名称 |
| total_amount | DECIMAL(18,2) | | 0 | 数字 | 合同总金额 |
| signed_at | DATE | | | 日期 | 签署日期 |
| effective_at | DATE | | | 日期 | 生效日期 |
| expire_at | DATE | | | 日期 | 到期日期 |
| stage | VARCHAR(50) | ✅ | '签署中' | 下拉单选 | 阶段：签署中、履约中、已完成、已终止 |
| status | VARCHAR(50) | | '正常' | 下拉单选 | 状态：正常、风险、已完结 |
| invoice_info | TEXT | | | 多行文本 | 开票信息 |
| owner_id | UUID → app_user | | | 人员选择 | 负责人 |

### 关联关系

- Contract → Opportunity（多对一）
- Contract → Quote（一对一）
- Contract → Account（多对一）
- Contract → Payment（一对多）
- Contract → Attachment

### 业务规则

- `contract_no` 规则：`CRM-{年份}-{4位流水号}`（如 CRM-2026-0001）
- 合同终止时需完结所有关联回款计划

---

## 七、回款计划（Payment）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| contract_id | UUID → Contract | ✅ | | 合同选择 | 所属合同 |
| planned_amount | DECIMAL(18,2) | ✅ | | 数字 | 计划回款金额 |
| planned_date | DATE | ✅ | | 日期 | 计划回款日期 |
| actual_amount | DECIMAL(18,2) | | 0 | 数字 | 实际回款金额 |
| actual_date | DATE | | | 日期 | 实际回款日期 |
| stage | VARCHAR(50) | | '待回款' | 下拉单选 | 阶段：待回款、已确认、逾期 |
| confirmed_by | UUID → app_user | | | 人员选择 | 财务确认人 |

### 业务规则

- 逾期自动标记：`planned_date < today AND stage != '已确认'`

---

## 八、待办与活动（Activity）

### 表单字段

| 字段 | 类型 | 必填 | 默认 | 控件 | 说明 |
|------|------|------|------|------|------|
| related_type | VARCHAR(50) | | | 下拉单选 | 关联类型：lead、account、opportunity、quote、contract |
| related_id | UUID | | | 自动关联 | 关联实体 ID |
| activity_type | VARCHAR(50) | ✅ | | 下拉单选 | 类型：电话、会议、任务、报价确认、回款复核 |
| title | VARCHAR(500) | ✅ | | 文本框 | 标题 |
| description | TEXT | | | 多行文本 | 描述 |
| due_at | TIMESTAMPTZ | | | 日期时间 | 截止时间 |
| completed_at | TIMESTAMPTZ | | | 日期时间 | 完成时间 |
| priority | VARCHAR(20) | | '中' | 下拉单选 | 优先级：高、中、低 |
| status | VARCHAR(50) | | '待办' | 下拉单选 | 状态：待办、进行中、已完成、逾期 |
| owner_id | UUID → app_user | | | 人员选择 | 负责人 |

### 关联关系

- Activity → 任意实体（通过 related_type + related_id 多态关联）

### 业务规则

- 逾期自动标记：`due_at < now AND status != '已完成'`

---

## 九、实现优先级建议

| 优先级 | 实体 | 原因 |
|--------|------|------|
| P0 | Opportunity + Account + Contact | 核心业务入口，前端已就绪 |
| P0 | Quote | 直接关联机会转化，Excel 上传即可 |
| P1 | Contract + Payment | 关联报价，需要合同编号规则 |
| P1 | Lead | 线索转换是增量功能 |
| P2 | Activity | 聚合视图，依赖其他实体就绪 |
