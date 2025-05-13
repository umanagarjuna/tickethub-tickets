CREATE TABLE events (
                        event_id STRING(36) NOT NULL,
                        name STRING(MAX) NOT NULL,
                        description STRING(MAX),
                        start_time TIMESTAMP,
                        venue STRING(MAX),
                        image_url STRING(MAX)
) PRIMARY KEY (event_id);

CREATE TABLE seat_categories (
                                 event_id STRING(36) NOT NULL,
                                 category_id STRING(36) NOT NULL,
                                 name STRING(MAX) NOT NULL,
                                 price NUMERIC,
                                 available_count INT64
) PRIMARY KEY (event_id, category_id),
    INTERLEAVE IN PARENT events ON DELETE CASCADE;
