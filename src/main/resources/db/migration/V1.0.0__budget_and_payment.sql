CREATE TABLE public.budgets
(
    id                uuid           NOT NULL,
    work_order_id     bigint         NOT NULL,
    saga_id           uuid           NOT NULL,
    branch_id         varchar(20),
    customer_document varchar(14)    NOT NULL,
    total             numeric(12, 2) NOT NULL,
    status            varchar(20)    NOT NULL,
    approval_deadline timestamptz    NOT NULL,
    approved_by       varchar(14),
    cancel_reason     varchar(40),
    created_at        timestamptz    NOT NULL,
    updated_at        timestamptz    NOT NULL,
    CONSTRAINT budgets_pkey PRIMARY KEY (id),
    CONSTRAINT budgets_work_order_id_key UNIQUE (work_order_id)
);

CREATE TABLE public.budget_items
(
    budget_id   uuid           NOT NULL,
    type        varchar(10)    NOT NULL,
    catalog_id  bigint         NOT NULL,
    description varchar(255)   NOT NULL,
    quantity    integer        NOT NULL,
    unit_price  numeric(12, 2) NOT NULL,
    CONSTRAINT budget_items_pkey PRIMARY KEY (budget_id, type, catalog_id),
    CONSTRAINT budget_items_budget_id_fkey FOREIGN KEY (budget_id) REFERENCES public.budgets (id)
);

CREATE TABLE public.payments
(
    id                     uuid           NOT NULL,
    budget_id              uuid           NOT NULL,
    work_order_id          bigint         NOT NULL,
    amount                 numeric(12, 2) NOT NULL,
    status                 varchar(20)    NOT NULL,
    preference_id          varchar(64),
    payment_link           varchar(512),
    mp_payment_id          varchar(64),
    refund_id              varchar(64),
    refund_idempotency_key uuid           NOT NULL,
    payment_deadline       timestamptz    NOT NULL,
    cancel_reason          varchar(40),
    created_at             timestamptz    NOT NULL,
    updated_at             timestamptz    NOT NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (id),
    CONSTRAINT payments_budget_id_key UNIQUE (budget_id),
    CONSTRAINT payments_budget_id_fkey FOREIGN KEY (budget_id) REFERENCES public.budgets (id)
);

CREATE TABLE public.payment_attempts
(
    id            bigserial   NOT NULL,
    payment_id    uuid        NOT NULL,
    mp_payment_id varchar(64) NOT NULL,
    status_detail varchar(80) NOT NULL,
    occurred_at   timestamptz NOT NULL,
    CONSTRAINT payment_attempts_pkey PRIMARY KEY (id),
    CONSTRAINT payment_attempts_notification_key UNIQUE (payment_id, mp_payment_id, status_detail),
    CONSTRAINT payment_attempts_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments (id)
);

CREATE INDEX IF NOT EXISTS payments_mp_payment_id_idx ON public.payments (mp_payment_id);
