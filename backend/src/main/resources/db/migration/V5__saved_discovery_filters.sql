-- Saved discovery filters.
--
-- Age, distance and "show me" already live on users as preferences (they also drive
-- auto-match). Everything else set in the filter sheet - interests, and the paid filters
-- such as height, intent, activity, family plans and habits - is stored here so it
-- survives a reload and follows the user across devices.
--
-- One JSON document per user rather than a column per filter: the filter set grows with
-- the product, and nothing queries inside it - it is read whole and applied in the service.
CREATE TABLE discovery_filters (
    user_id    UUID          PRIMARY KEY,
    filters    VARCHAR(4000) NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_discovery_filters_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
