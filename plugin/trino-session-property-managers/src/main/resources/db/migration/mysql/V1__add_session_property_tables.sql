CREATE TABLE IF NOT EXISTS session_specs (
    spec_id BIGINT NOT NULL AUTO_INCREMENT,
    user_regex VARCHAR(512),
    source_regex VARCHAR(512),
    query_type VARCHAR(512),
    group_regex VARCHAR(512),
    priority INT NOT NULL,
    PRIMARY KEY (spec_id)
);

CREATE TABLE IF NOT EXISTS session_client_tags (
    tag_spec_id BIGINT NOT NULL,
    client_tag VARCHAR(512) NOT NULL,
    PRIMARY KEY (tag_spec_id, client_tag),
    FOREIGN KEY (tag_spec_id) REFERENCES session_specs (spec_id)
);

CREATE TABLE IF NOT EXISTS session_property_values (
    property_spec_id BIGINT NOT NULL,
    session_property_name VARCHAR(512),
    session_property_value VARCHAR(512),
    PRIMARY KEY (property_spec_id, session_property_name),
    FOREIGN KEY (property_spec_id) REFERENCES session_specs (spec_id)
);
