-- Issue 9: dismissible alerts
ALTER TABLE career_events ADD COLUMN dismissed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_career_events_dismissed ON career_events (dismissed) WHERE dismissed = FALSE;
