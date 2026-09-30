-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

CREATE TABLE department (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE
);

CREATE TABLE access_role (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  name varchar(120) NOT NULL UNIQUE,
  scope varchar(20) NOT NULL
);

CREATE TABLE permission (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL
);

CREATE TABLE role_permission (role_id bigint NOT NULL, permission_code varchar(60) NOT NULL, PRIMARY KEY(role_id, permission_code), FOREIGN KEY(role_id) REFERENCES access_role(id), FOREIGN KEY(permission_code) REFERENCES permission(code));

CREATE TABLE nav_menu (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  permission_code varchar(60) NOT NULL,
  position int NOT NULL,
  enabled boolean NOT NULL
);

CREATE TABLE account (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  username varchar(60) NOT NULL UNIQUE,
  display_name varchar(120) NOT NULL,
  password_hash varchar(100) NOT NULL,
  role_id bigint NOT NULL,
  department_id bigint NOT NULL,
  supplier_id bigint NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (role_id) REFERENCES access_role(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);

CREATE TABLE audit_event (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  actor varchar(60) NOT NULL,
  action varchar(120) NOT NULL,
  object_id varchar(80) NOT NULL,
  department_id bigint NOT NULL,
  created_at timestamp(6) NOT NULL
);

CREATE TABLE system_setting (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  code varchar(60) NOT NULL UNIQUE,
  parameter_value varchar(200) NOT NULL
);

CREATE TABLE dictionary_entry (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  type varchar(60) NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  name_en varchar(120) NOT NULL,
  UNIQUE (type, code)
);


CREATE TABLE supplier (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL,
  code varchar(60) NOT NULL,
  name varchar(120) NOT NULL,
  category varchar(60) NOT NULL,
  contact varchar(200) NOT NULL,
  qualification varchar(2000) NOT NULL,
  valid_until date NULL,
  status varchar(30) NOT NULL,
  review_note varchar(1000) NOT NULL,
  submitted_by varchar(60) NOT NULL,
  department_id bigint NOT NULL,
  enabled boolean NOT NULL,
  FOREIGN KEY (department_id) REFERENCES department(id),
  UNIQUE (code)
);

CREATE TABLE rfq (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  version bigint NOT NULL,
  number varchar(60) NOT NULL,
  title varchar(200) NOT NULL,
  description varchar(2000) NOT NULL,
  category varchar(60) NOT NULL,
  currency varchar(3) NOT NULL,
  department_id bigint NOT NULL,
  status varchar(30) NOT NULL,
  submitted_by varchar(60) NOT NULL,
  created_by varchar(60) NOT NULL,
  created_at timestamp(6) NOT NULL,
  deadline timestamp(6) NOT NULL,
  minimum_quotes int NOT NULL,
  review_note varchar(1000) NOT NULL,
  selected_quote_id bigint NULL,
  selection_reason varchar(2000) NOT NULL,
  exception_reason varchar(1000) NOT NULL,
  selected_by varchar(60) NOT NULL,
  awarded_at timestamp(6) NULL,
  approved_by varchar(60) NOT NULL,
  award_snapshot text NULL,
  FOREIGN KEY (department_id) REFERENCES department(id),
  UNIQUE (number)
);

CREATE TABLE rfq_line (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  rfq_id bigint NOT NULL,
  item_code varchar(60) NOT NULL,
  name varchar(200) NOT NULL,
  specification varchar(1000) NOT NULL,
  unit varchar(30) NOT NULL,
  quantity decimal(16,3) NOT NULL,
  FOREIGN KEY (rfq_id) REFERENCES rfq(id),
  UNIQUE (rfq_id,item_code),
  CHECK(quantity>0)
);

CREATE TABLE invitation (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  rfq_id bigint NOT NULL,
  supplier_id bigint NOT NULL,
  supplier_name varchar(120) NOT NULL,
  FOREIGN KEY (rfq_id) REFERENCES rfq(id),
  FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  UNIQUE(rfq_id,supplier_id)
);

CREATE TABLE quote (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  rfq_id bigint NOT NULL,
  supplier_id bigint NOT NULL,
  supplier_name varchar(120) NOT NULL,
  revision int NOT NULL,
  status varchar(30) NOT NULL,
  terms varchar(2000) NOT NULL,
  lead_days int NOT NULL,
  valid_until timestamp(6) NULL,
  freight decimal(16,2) NOT NULL,
  net_total decimal(16,2) NOT NULL,
  tax_total decimal(16,2) NOT NULL,
  gross_total decimal(16,2) NOT NULL,
  submitted_at timestamp(6) NOT NULL,
  submitted_by varchar(60) NOT NULL,
  FOREIGN KEY (rfq_id) REFERENCES rfq(id),
  FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  UNIQUE(rfq_id,supplier_id,revision),
  CHECK (freight>=0 AND gross_total>=0 AND lead_days>=0)
);

CREATE TABLE quote_line (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  quote_id bigint NOT NULL,
  rfq_line_id bigint NOT NULL,
  unit_price decimal(16,4) NOT NULL,
  tax_rate decimal(5,2) NOT NULL,
  net_amount decimal(16,2) NOT NULL,
  tax_amount decimal(16,2) NOT NULL,
  FOREIGN KEY (quote_id) REFERENCES quote(id),
  FOREIGN KEY (rfq_line_id) REFERENCES rfq_line(id),
  UNIQUE(quote_id,rfq_line_id),
  CHECK (unit_price>=0 AND tax_rate>=0 AND tax_rate<=100)
);

CREATE TABLE mutation_stamp (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  rfq_id bigint NOT NULL,
  actor varchar(60) NOT NULL,
  request_key varchar(80) NOT NULL,
  fingerprint varchar(64) NOT NULL,
  FOREIGN KEY (rfq_id) REFERENCES rfq(id),
  UNIQUE(rfq_id,actor,request_key)
);

ALTER TABLE account ADD CONSTRAINT fk_account_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id);
CREATE INDEX ix_rfq_department_state ON rfq(department_id,status,created_at);
CREATE INDEX ix_quote_rfq_state ON quote(rfq_id,status,supplier_id);
CREATE INDEX ix_supplier_scope ON supplier(department_id,status);
CREATE INDEX ix_audit_scope_time ON audit_event(department_id,created_at);

ALTER TABLE rfq ADD CONSTRAINT fk_rfq_selected_quote FOREIGN KEY (selected_quote_id) REFERENCES quote(id);
