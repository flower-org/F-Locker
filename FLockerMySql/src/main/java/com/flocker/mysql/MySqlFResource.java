package com.flocker.mysql;

import com.flocker.core.FResource;
import com.flocker.core.FLock;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Immutable {@link FResource} backed by a row of the {@code resource} table plus its
 * {@code parenthood} relations.
 *
 * <p>{@link #parentIds()} are the <em>direct</em> parent ids of this resource and
 * {@link #descendantIds()} are <em>all</em> of its descendant ids, resolved
 * recursively from the {@code parenthood} parent/child edges.
 */
public final class MySqlFResource implements FResource<Long> {
    private final long id;
    private final List<Long> parentIds;
    private final List<Long> descendantIds;
    @Nullable private final FLock<Long> lock;

    public MySqlFResource(
            long id,
            List<Long> parentIds,
            List<Long> descendantIds,
            @Nullable FLock<Long> lock) {
        this.id = id;
        this.parentIds = Collections.unmodifiableList(parentIds);
        this.descendantIds = Collections.unmodifiableList(descendantIds);
        this.lock = lock;
    }

    @Override
    public Long id() {
        return id;
    }

    @Override
    public List<Long> parentIds() {
        return parentIds;
    }

    @Override
    public List<Long> descendantIds() {
        return descendantIds;
    }

    @Override
    @Nullable
    public FLock<?> lock() {
        return lock;
    }

    @Override
    public String toString() {
        return "MySqlFResource{id=" + id
                + ", parentIds=" + parentIds
                + ", descendantIds=" + descendantIds
                + ", lock=" + lock
                + '}';
    }
}
