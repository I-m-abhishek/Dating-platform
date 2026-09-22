-- Correctness and safety fixes identified against docs/dating-app-system-design.md.
--
-- Four changes, each independent:
--   1. Client-supplied idempotency key for likes.
--   2. Per-participant read watermark, replacing a read flag on every message row.
--   3. A deterministic tiebreaker index for message pagination.
--   4. Bidirectional age filtering support in discovery.

-- 1. LIKE IDEMPOTENCY ---------------------------------------------------------
--
-- A like is quota-limited, so a retry on a flaky mobile connection costs the user a
-- like from their daily allowance and can double-fire the match notification. The key
-- lets the server recognise the retry and replay the original outcome.
--
-- Partial index: rows without a key (older clients, server-initiated auto-match likes)
-- are not constrained, and NULLs would not collide under a plain unique index anyway.
ALTER TABLE likes ADD COLUMN client_like_id VARCHAR(64);

CREATE UNIQUE INDEX uk_likes_sender_client_id
    ON likes (sender_id, client_like_id)
    WHERE client_like_id IS NOT NULL;

-- 2. READ WATERMARK -----------------------------------------------------------
--
-- Marking a conversation read used to UPDATE every unread message row. One watermark
-- per participant is a single updatable row, and "has the peer read this message" is
-- then a timestamp comparison at read time.
--
-- The existing messages.read_at column is deliberately left in place: it still carries
-- the history for conversations read before this migration, and the watermark is
-- backfilled from it below so nothing regresses for existing data.
ALTER TABLE conversations ADD COLUMN user_a_last_read_at TIMESTAMPTZ;
ALTER TABLE conversations ADD COLUMN user_b_last_read_at TIMESTAMPTZ;

-- Backfill: a participant reads messages sent by the OTHER person, so user A's
-- watermark is the newest READ message whose sender is user B, and vice versa.
--
-- The sender filter has to live inside the aggregate. Taking MAX(created_at) over every
-- read message in the conversation would fold in the messages A sent and B read, pushing
-- A's watermark past messages A has not actually seen - and silently reporting them as
-- read. A correlated subquery returns NULL when nothing qualifies, which is exactly the
-- right starting value for someone who has read nothing.
UPDATE conversations c
SET user_a_last_read_at = (
    SELECT MAX(m.created_at)
    FROM messages m
    WHERE m.conversation_id = c.id
      AND m.sender_id = c.user_b_id
      AND m.read_at IS NOT NULL
);

UPDATE conversations c
SET user_b_last_read_at = (
    SELECT MAX(m.created_at)
    FROM messages m
    WHERE m.conversation_id = c.id
      AND m.sender_id = c.user_a_id
      AND m.read_at IS NOT NULL
);

-- 3. MESSAGE PAGINATION TIEBREAKER --------------------------------------------
--
-- History paged on created_at alone has no tiebreaker, so two messages sharing a
-- millisecond can be returned twice or skipped entirely across a page boundary. The
-- query now orders by (created_at, id); this index matches that ordering exactly.
CREATE INDEX idx_messages_conversation_created_id
    ON messages (conversation_id, created_at DESC, id DESC);

-- 4. BIDIRECTIONAL AGE FILTER -------------------------------------------------
--
-- Discovery already checked that a candidate's age falls inside the viewer's range.
-- It now also checks the viewer's age against the candidate's own range, which needs
-- those two columns to be selective.
CREATE INDEX idx_users_age_preferences
    ON users (preferred_min_age, preferred_max_age)
    WHERE status = 'ACTIVE';
