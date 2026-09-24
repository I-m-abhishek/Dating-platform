-- Indexes for query shapes that were running as scans or sorts.
--
--   1. Login / register look users up with upper(email) (Spring Data's IgnoreCase), which
--      the plain unique constraint on email cannot serve.
--   2. The Likes You tab filters by receiver and status and orders by type then recency.
--      The wider index serves the filter, the order (scanned backwards) and both badge
--      counts, so it replaces idx_likes_receiver_status rather than sitting beside it.
--   3. Comment replies are loaded by parent for a whole page of comments at once;
--      parent_comment_id had no index at all.

-- 1. CASE-INSENSITIVE EMAIL LOOKUP -------------------------------------------
CREATE INDEX idx_users_email_upper ON users (upper(email));

-- 2. INBOUND LIKES ------------------------------------------------------------
CREATE INDEX idx_likes_receiver_status_order ON likes (receiver_id, status, type, created_at);
DROP INDEX idx_likes_receiver_status;

-- 3. COMMENT REPLIES ----------------------------------------------------------
CREATE INDEX idx_photo_comments_parent
    ON photo_comments (parent_comment_id, created_at)
    WHERE parent_comment_id IS NOT NULL;
