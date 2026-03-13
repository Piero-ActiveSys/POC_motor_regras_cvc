ALTER TABLE outbox_events ADD COLUMN requested_by TEXT;
ALTER TABLE outbox_events ADD COLUMN detail TEXT;

