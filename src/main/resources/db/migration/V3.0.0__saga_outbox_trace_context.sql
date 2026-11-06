ALTER TABLE public.saga_outbox
    ADD COLUMN IF NOT EXISTS trace_context text;
