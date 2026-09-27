-- When a student leaves required fullscreen, the server stores the time; if they do not return
-- within the countdown, the attempt is submitted automatically. NULL = currently in fullscreen.
ALTER TABLE attempts ADD COLUMN fullscreen_exited_at TIMESTAMPTZ;
CREATE INDEX ix_attempts_fullscreen_exit ON attempts (fullscreen_exited_at) WHERE fullscreen_exited_at IS NOT NULL;
