package com.flocker.mysql;

import com.flocker.core.FResource;
import com.flocker.core.FLock;
import com.flocker.core.FLockerEngine;

import io.vertx.core.Future;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * {@link FLockerEngine} implementation backed by MySQL through the asynchronous
 * Vert.x MySQL client.
 *
 * <p>The engine relies on three tables:
 * <ul>
 *   <li>{@code lock(lock_id BIGINT PK, created_at TIMESTAMP)}</li>
 *   <li>{@code resource(resource_id BIGINT PK, lock_id BIGINT NULL FK -> lock, version BIGINT)}</li>
 *   <li>{@code parenthood(parent_resource_id BIGINT, child_resource_id BIGINT)} &ndash;
 *       the direct parent/child edges of the resource hierarchy; descendants are
 *       resolved recursively at query time.</li>
 * </ul>
 *
 * <p>Locking a resource sets {@code lock_id} on the resource and <em>all</em> of its
 * descendants (resolved recursively). The {@code lock} row must already exist (see
 * {@link #createLock()}); if the resource or any descendant is already locked, the
 * whole operation fails with a {@link LockConflictException} and no row is modified.
 */
public class MySqlFLockerEngine implements FLockerEngine<Long, Long> {

    private static final String COL_RESOURCE_ID = "resource_id";
    private static final String COL_LOCK_ID = "lock_id";
    private static final String COL_VERSION = "version";
    private static final String COL_PARENT_RESOURCE_ID = "parent_resource_id";
    private static final String COL_CHILD_RESOURCE_ID = "child_resource_id";

    private static final String CREATE_LOCK =
            "INSERT INTO `lock` () VALUES ()";
    private static final String DELETE_LOCK =
            "DELETE FROM `lock` WHERE lock_id = ?";
    private static final String SELECT_RESOURCES_FOR_UPDATE =
            "SELECT resource_id, lock_id FROM resource WHERE resource_id IN (%s) FOR UPDATE";
    private static final String LOCK_RESOURCES =
            "UPDATE resource SET lock_id = ?, version = version + 1 WHERE resource_id IN (%s)";
    private static final String UNLOCK_RESOURCES =
            "UPDATE resource SET lock_id = NULL, version = version + 1"
                    + " WHERE lock_id = ? AND resource_id IN (%s)";
    private static final String SELECT_RESOURCE_IDS_BY_LOCK =
            "SELECT resource_id FROM resource WHERE lock_id = ? FOR UPDATE";
    private static final String SELECT_RESOURCE =
            "SELECT resource_id, lock_id, version FROM resource WHERE resource_id = ?";
    private static final String SELECT_PARENT_IDS =
            "SELECT parent_resource_id FROM parenthood WHERE child_resource_id = ?";
    /** Recursively resolves every descendant of a resource from the parent/child edges. */
    private static final String SELECT_DESCENDANT_IDS =
            "WITH RECURSIVE descendants AS ("
                    + " SELECT child_resource_id FROM parenthood WHERE parent_resource_id = ?"
                    + " UNION"
                    + " SELECT p.child_resource_id FROM parenthood p"
                    + " JOIN descendants d ON p.parent_resource_id = d.child_resource_id"
                    + ") SELECT child_resource_id FROM descendants";

    private final Pool pool;

    public MySqlFLockerEngine(Pool pool) {
        this.pool = pool;
    }

    @Override
    public CompletableFuture<FResource<Long>> getResource(Long resourceId) {
        return pool.withConnection(conn -> loadEntity(conn, resourceId))
                .toCompletionStage()
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<List<FResource<Long>>> getResources(List<Long> resourceIds) {
        return pool.withConnection(conn -> loadEntities(conn, resourceIds))
                .toCompletionStage()
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<List<FResource<Long>>> lockResource(Long resourceId, Long lockId) {
        long root = resourceId;
        long lock = lockId;
        Future<List<FResource<Long>>> future = pool.withTransaction(conn ->
                descendantAndSelfIds(conn, root)
                        .flatMap(ids -> lockRowsForUpdate(conn, ids)
                                .flatMap(rows -> ensureLockable(rows, root))
                                .flatMap(ignored -> applyLock(conn, ids, lock))
                                .flatMap(ignored -> loadEntities(conn, ids))));
        return future.toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<List<FResource<Long>>> unlockResource(Long resourceId, Long lockId) {
        long root = resourceId;
        long lock = lockId;
        Future<List<FResource<Long>>> future = pool.withTransaction(conn ->
                descendantAndSelfIds(conn, root)
                        .flatMap(ids -> clearLock(conn, ids, lock)
                                .flatMap(ignored -> loadEntities(conn, ids))));
        return future.toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<List<FResource<Long>>> unlockAll(Long lockId) {
        long lock = lockId;
        Future<List<FResource<Long>>> future = pool.withTransaction(conn ->
                lockedResourceIds(conn, lock).flatMap(ids -> {
                    if (ids.isEmpty()) {
                        return Future.<List<FResource<Long>>>succeededFuture(List.of());
                    }
                    return clearLock(conn, ids, lock)
                            .flatMap(ignored -> loadEntities(conn, ids));
                }));
        return future.toCompletionStage().toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> createLock() {
        return pool.withConnection(conn ->
                        conn.preparedQuery(CREATE_LOCK)
                                .execute()
                                .map(rows -> rows.property(MySQLClient.LAST_INSERTED_ID)))
                .toCompletionStage()
                .toCompletableFuture();
    }

    @Override
    public CompletableFuture<Long> deleteLock(Long lockId) {
        return pool.withConnection(conn ->
                        conn.preparedQuery(DELETE_LOCK)
                                .execute(Tuple.of(lockId))
                                .map(ignored -> lockId))
                .toCompletionStage()
                .toCompletableFuture();
    }

    /**
     * Returns the root resource id followed by <em>all</em> of its descendant ids.
     *
     * <p>Descendants are resolved recursively (children, their children, and so on)
     * by the {@link #SELECT_DESCENDANT_IDS} CTE over the {@code parenthood} edges.
     */
    private Future<List<Long>> descendantAndSelfIds(SqlConnection conn, long rootId) {
        return conn.preparedQuery(SELECT_DESCENDANT_IDS)
                .execute(Tuple.of(rootId))
                .map(rows -> {
                    List<Long> ids = new ArrayList<>();
                    ids.add(rootId);
                    for (Row row : rows) {
                        ids.add(row.getLong(COL_CHILD_RESOURCE_ID));
                    }
                    return ids;
                });
    }

    /**
     * Locks the given resource rows {@code FOR UPDATE} and returns their
     * {@code (resource_id, lock_id)} rows for lock-state inspection.
     */
    private Future<List<Row>> lockRowsForUpdate(SqlConnection conn, List<Long> ids) {
        return conn.preparedQuery(String.format(SELECT_RESOURCES_FOR_UPDATE, placeholders(ids.size())))
                .execute(idTuple(ids))
                .map(rows -> {
                    List<Row> list = new ArrayList<>();
                    for (Row row : rows) {
                        list.add(row);
                    }
                    return list;
                });
    }

    /**
     * Succeeds only if the whole subtree is lockable: fails with a
     * {@link LockConflictException} if any row already holds a lock, or a
     * {@link NoSuchElementException} if the root resource row is missing.
     */
    private Future<Void> ensureLockable(List<Row> rows, long rootId) {
        boolean rootExists = false;
        for (Row row : rows) {
            long rowId = row.getLong(COL_RESOURCE_ID);
            if (rowId == rootId) {
                rootExists = true;
            }
            Long heldBy = row.getLong(COL_LOCK_ID);
            if (heldBy != null) {
                return Future.failedFuture(new LockConflictException(rowId, heldBy));
            }
        }
        if (!rootExists) {
            return Future.failedFuture(new NoSuchElementException("Resource not found: " + rootId));
        }
        return Future.succeededFuture();
    }

    /** Sets {@code lock_id} on every given resource. */
    private Future<Void> applyLock(SqlConnection conn, List<Long> ids, long lockId) {
        Tuple params = Tuple.of(lockId);
        for (Long id : ids) {
            params.addValue(id);
        }
        return conn.preparedQuery(String.format(LOCK_RESOURCES, placeholders(ids.size())))
                .execute(params)
                .mapEmpty();
    }

    /** Clears {@code lock_id} on every given resource currently held by {@code lockId}. */
    private Future<Void> clearLock(SqlConnection conn, List<Long> ids, long lockId) {
        Tuple params = Tuple.of(lockId);
        for (Long id : ids) {
            params.addValue(id);
        }
        return conn.preparedQuery(String.format(UNLOCK_RESOURCES, placeholders(ids.size())))
                .execute(params)
                .mapEmpty();
    }

    /** Locks {@code FOR UPDATE} and returns the ids of every resource currently held by {@code lockId}. */
    private Future<List<Long>> lockedResourceIds(SqlConnection conn, long lockId) {
        return conn.preparedQuery(SELECT_RESOURCE_IDS_BY_LOCK)
                .execute(Tuple.of(lockId))
                .map(rows -> toLongList(rows, COL_RESOURCE_ID));
    }

    private Future<List<FResource<Long>>> loadEntities(SqlConnection conn, List<Long> entityIds) {
        List<Future<FResource<Long>>> futures = new ArrayList<>();
        for (Long id : entityIds) {
            futures.add(loadEntity(conn, id));
        }
        if (futures.isEmpty()) {
            return Future.succeededFuture(new ArrayList<>());
        }
        List<Future<?>> raw = new ArrayList<>(futures);
        return Future.all(raw).map(composite -> {
            List<FResource<Long>> result = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                result.add(composite.resultAt(i));
            }
            return result;
        });
    }

    private Future<FResource<Long>> loadEntity(SqlConnection conn, long entityId) {
        return conn.preparedQuery(SELECT_RESOURCE)
                .execute(Tuple.of(entityId))
                .flatMap(rows -> {
                    Row row = firstRow(rows);
                    if (row == null) {
                        return Future.failedFuture(
                                new NoSuchElementException("Entity not found: " + entityId));
                    }
                    Long lockerId = row.getLong(COL_LOCK_ID);
                    long version = row.getLong(COL_VERSION);
                    FLock<Long> lock = (lockerId == null) ? null : new MySqlFLock(lockerId);

                    Future<List<Long>> parentsFuture = conn.preparedQuery(SELECT_PARENT_IDS)
                            .execute(Tuple.of(entityId))
                            .map(r -> toLongList(r, COL_PARENT_RESOURCE_ID));
                    Future<List<Long>> descendantsFuture = conn.preparedQuery(SELECT_DESCENDANT_IDS)
                            .execute(Tuple.of(entityId))
                            .map(r -> toLongList(r, COL_CHILD_RESOURCE_ID));

                    return Future.all(parentsFuture, descendantsFuture).map(composite -> {
                        List<Long> parents = composite.resultAt(0);
                        List<Long> descendants = composite.resultAt(1);
                        return new MySqlFResource(entityId, parents, descendants, lock, version);
                    });
                });
    }

    @Nullable
    private static Row firstRow(RowSet<Row> rows) {
        for (Row row : rows) {
            return row;
        }
        return null;
    }

    private static List<Long> toLongList(RowSet<Row> rows, String column) {
        List<Long> result = new ArrayList<>();
        for (Row row : rows) {
            result.add(row.getLong(column));
        }
        return result;
    }

    private static Tuple idTuple(List<Long> ids) {
        Tuple tuple = Tuple.tuple();
        for (Long id : ids) {
            tuple.addValue(id);
        }
        return tuple;
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        return sb.toString();
    }
}
