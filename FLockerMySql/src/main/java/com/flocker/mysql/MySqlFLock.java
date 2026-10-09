package com.flocker.mysql;

import com.flocker.core.FLock;

/** A lock identified by its numeric {@code lock_id}. */
public final class MySqlFLock implements FLock<Long> {
    private final long id;

    public MySqlFLock(long id) {
        this.id = id;
    }

    @Override
    public Long id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MySqlFLock)) {
            return false;
        }
        return id == ((MySqlFLock) o).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "MySqlFLock{id=" + id + '}';
    }
}
