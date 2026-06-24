-- ============================================================================
-- V7__business_schema.sql
-- 业务核心表：Lead / Account / Contact / Opportunity / Quote / Contract / Payment / Activity
--
-- 所有主键使用 UUID + gen_random_uuid()，外键约束完整，包含 CHECK 约束与索引。
-- 参考 V1__foundation_schema.sql 的写法风格。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Lead（线索）
-- ---------------------------------------------------------------------------
CREATE TABLE lead (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    company             VARCHAR(200),
    title               VARCHAR(200),
    email               VARCHAR(200),
    phone               VARCHAR(50),
    mobile              VARCHAR(50),
    website             VARCHAR(500),
    industry            VARCHAR(100),
    lead_source         VARCHAR(100),
    status              VARCHAR(50),
    rating              VARCHAR(50),
    annual_revenue      DECIMAL(18,2),
    number_of_employees INTEGER,
    street              VARCHAR(500),
    city                VARCHAR(100),
    state               VARCHAR(100),
    zip_code            VARCHAR(20),
    country             VARCHAR(100),
    description         TEXT,
    assigned_to_id      UUID,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_lead PRIMARY KEY (id),
    CONSTRAINT fk_lead_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES app_user (id)
);

COMMENT ON TABLE lead IS '线索';
COMMENT ON COLUMN lead.name IS '线索名称/联系人姓名';
COMMENT ON COLUMN lead.company IS '公司名称';
COMMENT ON COLUMN lead.lead_source IS '线索来源';
COMMENT ON COLUMN lead.status IS '线索状态';
COMMENT ON COLUMN lead.rating IS '线索评级';

-- 线索状态 CHECK
ALTER TABLE lead ADD CONSTRAINT ck_lead_status CHECK (
    status IS NULL OR status IN (
        'new', 'contacted', 'qualifying', 'qualified', 'unqualified', 'converted', 'lost'
    )
);

-- 线索评级 CHECK
ALTER TABLE lead ADD CONSTRAINT ck_lead_rating CHECK (
    rating IS NULL OR rating IN ('hot', 'warm', 'cold')
);

CREATE INDEX idx_lead_status ON lead (status) WHERE is_deleted = FALSE;
CREATE INDEX idx_lead_assigned_to ON lead (assigned_to_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_lead_created_at ON lead (created_at DESC) WHERE is_deleted = FALSE;
CREATE INDEX idx_lead_company ON lead (company) WHERE is_deleted = FALSE;


-- ---------------------------------------------------------------------------
-- 2. Account（客户）
-- ---------------------------------------------------------------------------
CREATE TABLE account (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    website             VARCHAR(500),
    phone               VARCHAR(50),
    fax                 VARCHAR(50),
    industry            VARCHAR(100),
    account_type        VARCHAR(50),
    ownership           VARCHAR(50),
    annual_revenue      DECIMAL(18,2),
    number_of_employees INTEGER,
    billing_street      VARCHAR(500),
    billing_city        VARCHAR(100),
    billing_state       VARCHAR(100),
    billing_zip_code    VARCHAR(20),
    billing_country     VARCHAR(100),
    shipping_street     VARCHAR(500),
    shipping_city       VARCHAR(100),
    shipping_state      VARCHAR(100),
    shipping_zip_code   VARCHAR(20),
    shipping_country    VARCHAR(100),
    description         TEXT,
    owner_id            UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT fk_account_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

COMMENT ON TABLE account IS '客户';
COMMENT ON COLUMN account.account_type IS '客户类型';
COMMENT ON COLUMN account.ownership IS '所有权';

-- 客户类型 CHECK
ALTER TABLE account ADD CONSTRAINT ck_account_type CHECK (
    account_type IS NULL OR account_type IN (
        'prospect', 'customer', 'partner', 'reseller', 'competitor', 'vendor'
    )
);

-- 所有权 CHECK
ALTER TABLE account ADD CONSTRAINT ck_account_ownership CHECK (
    ownership IS NULL OR ownership IN ('public', 'private', 'government', 'other')
);

CREATE INDEX idx_account_name ON account (name) WHERE is_deleted = FALSE;
CREATE INDEX idx_account_owner ON account (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_account_industry ON account (industry) WHERE is_deleted = FALSE;
CREATE INDEX idx_account_created_at ON account (created_at DESC) WHERE is_deleted = FALSE;


-- ---------------------------------------------------------------------------
-- 3. Contact（联系人）
-- ---------------------------------------------------------------------------
CREATE TABLE contact (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(200),
    phone           VARCHAR(50),
    mobile          VARCHAR(50),
    title           VARCHAR(200),
    department      VARCHAR(200),
    birthdate       DATE,
    reporting_to    VARCHAR(200),
    source          VARCHAR(100),
    description     TEXT,
    street          VARCHAR(500),
    city            VARCHAR(100),
    state           VARCHAR(100),
    zip_code        VARCHAR(20),
    country         VARCHAR(100),
    account_id      UUID,
    owner_id        UUID,
    reports_to_id   UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_contact PRIMARY KEY (id),
    CONSTRAINT fk_contact_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_contact_owner FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT fk_contact_reports_to FOREIGN KEY (reports_to_id) REFERENCES contact (id)
);

COMMENT ON TABLE contact IS '联系人';
COMMENT ON COLUMN contact.source IS '联系人来源';

CREATE INDEX idx_contact_account ON contact (account_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_contact_owner ON contact (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_contact_reports_to ON contact (reports_to_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_contact_name ON contact (last_name, first_name) WHERE is_deleted = FALSE;
CREATE INDEX idx_contact_email ON contact (email) WHERE is_deleted = FALSE AND email IS NOT NULL;


-- ---------------------------------------------------------------------------
-- 4. Opportunity（商机）
-- ---------------------------------------------------------------------------
CREATE TABLE opportunity (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    amount           DECIMAL(18,2),
    stage            VARCHAR(50),
    probability      INTEGER,
    close_date       DATE,
    opportunity_type VARCHAR(50),
    lead_source      VARCHAR(100),
    next_step        VARCHAR(500),
    description      TEXT,
    account_id       UUID,
    contact_id       UUID,
    owner_id         UUID,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_opportunity PRIMARY KEY (id),
    CONSTRAINT fk_opportunity_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_opportunity_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT fk_opportunity_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

COMMENT ON TABLE opportunity IS '商机';
COMMENT ON COLUMN opportunity.stage IS '销售阶段';
COMMENT ON COLUMN opportunity.probability IS '赢率（0-100）';

-- 赢率范围 CHECK
ALTER TABLE opportunity ADD CONSTRAINT ck_opportunity_probability CHECK (
    probability IS NULL OR (probability >= 0 AND probability <= 100)
);

-- 商机阶段 CHECK
ALTER TABLE opportunity ADD CONSTRAINT ck_opportunity_stage CHECK (
    stage IS NULL OR stage IN (
        'prospecting', 'qualification', 'needs_analysis', 'value_proposition',
        'proposal', 'negotiation', 'closed_won', 'closed_lost'
    )
);

CREATE INDEX idx_opportunity_account ON opportunity (account_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_opportunity_contact ON opportunity (contact_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_opportunity_owner ON opportunity (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_opportunity_stage ON opportunity (stage) WHERE is_deleted = FALSE;
CREATE INDEX idx_opportunity_close_date ON opportunity (close_date) WHERE is_deleted = FALSE;


-- ---------------------------------------------------------------------------
-- 5. Quote（报价）
--
-- 注意：报价明细不通过数据库表实现，通过附件上传 Excel 文件。
-- ---------------------------------------------------------------------------
CREATE TABLE quote (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    quote_number        VARCHAR(100),
    stage               VARCHAR(50),
    subtotal            DECIMAL(18,2),
    discount            DECIMAL(18,2),
    tax                 DECIMAL(18,2),
    tax_rate            DECIMAL(18,2),
    shipping            DECIMAL(18,2),
    total_amount        DECIMAL(18,2),
    currency            VARCHAR(10),
    valid_until         DATE,
    terms_and_conditions TEXT,
    description         TEXT,
    opportunity_id      UUID,
    account_id          UUID,
    contact_id          UUID,
    owner_id            UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_quote PRIMARY KEY (id),
    CONSTRAINT uq_quote_number UNIQUE (quote_number),
    CONSTRAINT fk_quote_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunity (id),
    CONSTRAINT fk_quote_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_quote_contact FOREIGN KEY (contact_id) REFERENCES contact (id),
    CONSTRAINT fk_quote_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

COMMENT ON TABLE quote IS '报价';
COMMENT ON COLUMN quote.quote_number IS '报价编号（业务唯一）';
COMMENT ON COLUMN quote.stage IS '报价阶段';
COMMENT ON COLUMN quote.currency IS '币种（如 CNY/USD）';

-- 报价阶段 CHECK
ALTER TABLE quote ADD CONSTRAINT ck_quote_stage CHECK (
    stage IS NULL OR stage IN ('draft', 'sent', 'reviewed', 'accepted', 'rejected', 'expired')
);

-- 折扣检查
ALTER TABLE quote ADD CONSTRAINT ck_quote_discount CHECK (
    discount IS NULL OR discount >= 0
);

-- 税率检查
ALTER TABLE quote ADD CONSTRAINT ck_quote_tax_rate CHECK (
    tax_rate IS NULL OR tax_rate >= 0
);

CREATE INDEX idx_quote_opportunity ON quote (opportunity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_quote_account ON quote (account_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_quote_owner ON quote (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_quote_stage ON quote (stage) WHERE is_deleted = FALSE;


-- ---------------------------------------------------------------------------
-- 6. Contract（合同）
-- ---------------------------------------------------------------------------
CREATE TABLE contract (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                    VARCHAR(200) NOT NULL,
    contract_number         VARCHAR(100),
    status                  VARCHAR(50),
    contract_type           VARCHAR(50),
    start_date              DATE,
    end_date                DATE,
    total_amount            DECIMAL(18,2),
    currency                VARCHAR(10),
    signed_date             DATE,
    expiration_notice_days  INTEGER,
    description             TEXT,
    terms                   TEXT,
    signed_at               TIMESTAMP,
    account_id              UUID,
    opportunity_id          UUID,
    owner_id                UUID,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_contract PRIMARY KEY (id),
    CONSTRAINT uq_contract_number UNIQUE (contract_number),
    CONSTRAINT fk_contract_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT fk_contract_opportunity FOREIGN KEY (opportunity_id) REFERENCES opportunity (id),
    CONSTRAINT fk_contract_owner FOREIGN KEY (owner_id) REFERENCES app_user (id)
);

COMMENT ON TABLE contract IS '合同';
COMMENT ON COLUMN contract.contract_number IS '合同编号（业务唯一）';
COMMENT ON COLUMN contract.status IS '合同状态';
COMMENT ON COLUMN contract.contract_type IS '合同类型';
COMMENT ON COLUMN contract.expiration_notice_days IS '到期提前提醒天数';

-- 合同状态 CHECK
ALTER TABLE contract ADD CONSTRAINT ck_contract_status CHECK (
    status IS NULL OR status IN (
        'draft', 'pending_approval', 'active', 'completed', 'terminated', 'expired', 'renewed'
    )
);

-- 日期范围 CHECK
ALTER TABLE contract ADD CONSTRAINT ck_contract_date_range CHECK (
    start_date IS NULL OR end_date IS NULL OR end_date >= start_date
);

CREATE INDEX idx_contract_account ON contract (account_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_contract_opportunity ON contract (opportunity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_contract_owner ON contract (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_contract_status ON contract (status) WHERE is_deleted = FALSE;
CREATE INDEX idx_contract_end_date ON contract (end_date) WHERE is_deleted = FALSE AND end_date IS NOT NULL;


-- ---------------------------------------------------------------------------
-- 7. Payment（付款记录）
-- ---------------------------------------------------------------------------
CREATE TABLE payment (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    amount            DECIMAL(18,2) NOT NULL,
    payment_date      DATE,
    due_date          DATE,
    payment_method    VARCHAR(50),
    status            VARCHAR(50),
    reference_number  VARCHAR(200),
    notes             TEXT,
    paid_at           TIMESTAMP,
    contract_id       UUID         NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT fk_payment_contract FOREIGN KEY (contract_id) REFERENCES contract (id)
);

COMMENT ON TABLE payment IS '付款记录';
COMMENT ON COLUMN payment.payment_method IS '付款方式';
COMMENT ON COLUMN payment.status IS '付款状态';
COMMENT ON COLUMN payment.reference_number IS '付款参考号';

-- 金额检查
ALTER TABLE payment ADD CONSTRAINT ck_payment_amount CHECK (amount >= 0);

-- 付款状态 CHECK
ALTER TABLE payment ADD CONSTRAINT ck_payment_status CHECK (
    status IS NULL OR status IN ('pending', 'partially_paid', 'paid', 'overdue', 'cancelled', 'refunded')
);

-- 付款方式 CHECK
ALTER TABLE payment ADD CONSTRAINT ck_payment_method CHECK (
    payment_method IS NULL OR payment_method IN (
        'bank_transfer', 'credit_card', 'debit_card', 'wechat', 'alipay', 'cash', 'check', 'other'
    )
);

CREATE INDEX idx_payment_contract ON payment (contract_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_payment_status ON payment (status) WHERE is_deleted = FALSE;
CREATE INDEX idx_payment_due_date ON payment (due_date) WHERE is_deleted = FALSE;
CREATE INDEX idx_payment_payment_date ON payment (payment_date DESC) WHERE is_deleted = FALSE;


-- ---------------------------------------------------------------------------
-- 8. Activity（活动/任务记录）
-- ---------------------------------------------------------------------------
CREATE TABLE activity (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    subject                 VARCHAR(500) NOT NULL,
    activity_type           VARCHAR(50)  NOT NULL,
    status                  VARCHAR(50),
    priority                VARCHAR(50),
    description             TEXT,
    due_date                DATE,
    start_time              TIMESTAMP,
    end_time                TIMESTAMP,
    duration_minutes        INTEGER,
    location                VARCHAR(500),
    is_completed            BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at            TIMESTAMP,
    ref_entity_type         VARCHAR(100),
    ref_entity_id           VARCHAR(36),
    is_all_day              BOOLEAN      NOT NULL DEFAULT FALSE,
    reminder_minutes_before INTEGER,
    assigned_to_id          UUID,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID,
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_activity PRIMARY KEY (id),
    CONSTRAINT fk_activity_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES app_user (id)
);

COMMENT ON TABLE activity IS '活动/任务记录';
COMMENT ON COLUMN activity.activity_type IS '活动类型';
COMMENT ON COLUMN activity.status IS '活动状态';
COMMENT ON COLUMN activity.priority IS '优先级';
COMMENT ON COLUMN activity.ref_entity_type IS '关联实体类型（如 lead/account/contact/opportunity）';
COMMENT ON COLUMN activity.ref_entity_id IS '关联实体 ID';
COMMENT ON COLUMN activity.reminder_minutes_before IS '提前提醒分钟数';

-- 活动类型 CHECK
ALTER TABLE activity ADD CONSTRAINT ck_activity_type CHECK (
    activity_type IN (
        'call', 'meeting', 'email', 'task', 'note', 'event', 'reminder', 'other'
    )
);

-- 活动状态 CHECK
ALTER TABLE activity ADD CONSTRAINT ck_activity_status CHECK (
    status IS NULL OR status IN ('not_started', 'in_progress', 'completed', 'deferred', 'cancelled')
);

-- 优先级 CHECK
ALTER TABLE activity ADD CONSTRAINT ck_activity_priority CHECK (
    priority IS NULL OR priority IN ('low', 'normal', 'high', 'urgent')
);

-- 已完成时 completed_at 必须非空的一致性约束（简化：仅当 is_completed = TRUE 时鼓励填写）
-- 时间范围 CHECK
ALTER TABLE activity ADD CONSTRAINT ck_activity_time_range CHECK (
    start_time IS NULL OR end_time IS NULL OR end_time >= start_time
);

-- 持续时间检查
ALTER TABLE activity ADD CONSTRAINT ck_activity_duration CHECK (
    duration_minutes IS NULL OR duration_minutes > 0
);

CREATE INDEX idx_activity_assigned_to ON activity (assigned_to_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_type ON activity (activity_type) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_status ON activity (status) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_due_date ON activity (due_date) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_ref_entity ON activity (ref_entity_type, ref_entity_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_activity_created_at ON activity (created_at DESC) WHERE is_deleted = FALSE;
