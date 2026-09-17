CREATE TABLE workspaces (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(64) NOT NULL,
    password_hash   TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    token_version   INT NOT NULL DEFAULT 1,
    created_at      BIGINT NOT NULL
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    login_id        VARCHAR(64),
    name            VARCHAR(64) NOT NULL,
    role            VARCHAR(16) NOT NULL CHECK (role IN ('MANAGER', 'STAFF')),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    password_hash   TEXT,
    created_at      BIGINT NOT NULL,
    UNIQUE (workspace_id, name)
);

CREATE TABLE materials (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    code_no         INT NOT NULL,
    name            TEXT NOT NULL,
    unit            TEXT NOT NULL,
    pack_unit       TEXT NOT NULL DEFAULT '',
    unit_price      DOUBLE PRECISION NOT NULL DEFAULT 0,
    safety_stock    DOUBLE PRECISION NOT NULL DEFAULT 0,
    lead_time_days  INT NOT NULL DEFAULT 0,
    note            TEXT NOT NULL DEFAULT '',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      BIGINT NOT NULL,
    UNIQUE (workspace_id, code_no)
);

CREATE TABLE opening_stocks (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    material_id     BIGINT NOT NULL REFERENCES materials(id),
    year_month      CHAR(7) NOT NULL,
    qty             DOUBLE PRECISION NOT NULL,
    UNIQUE (workspace_id, material_id, year_month)
);

CREATE TABLE stock_movements (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    client_uid      VARCHAR(64),
    material_id     BIGINT NOT NULL REFERENCES materials(id),
    type            VARCHAR(16) NOT NULL CHECK (type IN ('INBOUND', 'OUTBOUND', 'SCRAP', 'ADJUST')),
    qty             DOUBLE PRECISION NOT NULL,
    unit_price      DOUBLE PRECISION NOT NULL DEFAULT 0,
    occurred_on     DATE NOT NULL,
    note            TEXT NOT NULL DEFAULT '',
    created_at      BIGINT NOT NULL,
    created_by      BIGINT REFERENCES users(id),
    created_by_name TEXT NOT NULL DEFAULT '',
    UNIQUE (workspace_id, client_uid)
);

CREATE INDEX stock_movements_month_idx
    ON stock_movements (workspace_id, occurred_on, material_id);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    code_no         INT NOT NULL,
    name            TEXT NOT NULL,
    sell_price      DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      BIGINT NOT NULL,
    UNIQUE (workspace_id, code_no)
);

CREATE TABLE product_bom (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    material_id     BIGINT NOT NULL REFERENCES materials(id),
    us_qty          DOUBLE PRECISION NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (product_id, material_id)
);

CREATE TABLE daily_production (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    client_uid      VARCHAR(64),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    work_date       DATE NOT NULL,
    qty             INT NOT NULL,
    updated_at      BIGINT NOT NULL,
    updated_by      BIGINT REFERENCES users(id),
    UNIQUE (workspace_id, product_id, work_date)
);

CREATE UNIQUE INDEX daily_production_uid_idx
    ON daily_production (workspace_id, client_uid)
    WHERE client_uid IS NOT NULL;

CREATE TABLE product_plans (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    year_month      CHAR(7) NOT NULL,
    qty             INT NOT NULL,
    UNIQUE (workspace_id, product_id, year_month)
);

CREATE TABLE finished_goods (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    code_no         INT NOT NULL,
    name            TEXT NOT NULL,
    sell_price      DOUBLE PRECISION NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      BIGINT NOT NULL,
    UNIQUE (workspace_id, code_no)
);

CREATE TABLE finished_composition (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    finished_good_id BIGINT NOT NULL REFERENCES finished_goods(id),
    product_id      BIGINT NOT NULL REFERENCES products(id),
    sort_order      INT NOT NULL DEFAULT 0,
    UNIQUE (finished_good_id, product_id)
);

CREATE TABLE monthly_plans (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    finished_good_id BIGINT NOT NULL REFERENCES finished_goods(id),
    year_month      CHAR(7) NOT NULL,
    qty             INT NOT NULL,
    UNIQUE (workspace_id, finished_good_id, year_month)
);

CREATE TABLE month_closes (
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    year_month      CHAR(7) NOT NULL,
    closed_at       BIGINT NOT NULL,
    closed_by       BIGINT REFERENCES users(id),
    closed_by_name  TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (workspace_id, year_month)
);
