-- Sign-in is Google-only (ARCHITECTURE.md §6.1); passwords are no longer stored.
-- Accounts that had one stay and link to Google by email on their first Google login.
ALTER TABLE users DROP COLUMN password_hash;
