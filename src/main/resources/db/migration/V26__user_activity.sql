-- The operator page's usage over time (ARCHITECTURE.md §3.5): a user is active on a day
-- (UTC) when one of their sessions starts or refreshes, so one row per user and day.

CREATE TABLE user_activity
(
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE NOT NULL,
    PRIMARY KEY (user_id, day)
);

CREATE INDEX user_activity_day_idx ON user_activity (day);
