-- V14__Create_notifications.sql
-- Creates notifications table for in-app notifications

CREATE TABLE notifications
(
    notification_id SERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT,
    reference_type  VARCHAR(50),
    reference_id    INTEGER,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_unread ON notifications (user_id, is_read);
CREATE INDEX idx_notifications_created ON notifications (user_id, created_at DESC);
