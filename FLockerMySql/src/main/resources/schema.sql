-- Schema for the MySQL-backed Flocker LockEngine.

-- `lock` is a MySQL reserved word, so the table name is always backtick-quoted.
CREATE TABLE IF NOT EXISTS `lock` (
    lock_id    BIGINT    NOT NULL AUTO_INCREMENT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lock_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS resource (
    resource_id  BIGINT      NOT NULL,
    lock_id      BIGINT      NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (resource_id),
    KEY idx_resource_lock (lock_id),
    CONSTRAINT fk_resource_lock FOREIGN KEY (lock_id) REFERENCES `lock` (lock_id)
) ENGINE=InnoDB;

-- Adjacency table: one row per direct parent -> child edge of the hierarchy.
-- Descendants are resolved recursively at query time (recursive CTE).
CREATE TABLE IF NOT EXISTS parenthood (
    parent_resource_id BIGINT NOT NULL,
    child_resource_id  BIGINT NOT NULL,
    PRIMARY KEY (parent_resource_id, child_resource_id),
    KEY idx_parenthood_child (child_resource_id)
) ENGINE=InnoDB;
