-- user module (M7). Emails are stored lower-cased by the application.

CREATE TABLE users
(
    id             UUID PRIMARY KEY,
    email          TEXT                     NOT NULL,
    display_name   TEXT                     NOT NULL,
    password_hash  TEXT,
    google_subject TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX users_email_uk ON users (email);
CREATE UNIQUE INDEX users_google_subject_uk ON users (google_subject);
