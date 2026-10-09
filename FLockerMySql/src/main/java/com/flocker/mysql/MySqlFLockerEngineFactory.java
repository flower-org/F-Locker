package com.flocker.mysql;

import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

/** Convenience factory for building a {@link MySqlFLockerEngine} and its Vert.x pool. */
public final class MySqlFLockerEngineFactory {

    private MySqlFLockerEngineFactory() {
    }

    /**
     * Creates a pooled {@link MySqlFLockerEngine}. The caller owns the returned
     * {@link Pool} and is responsible for closing it.
     */
    public static MySqlFLockerEngine create(MySQLConnectOptions connectOptions, PoolOptions poolOptions) {
        Pool pool = MySQLBuilder.pool()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .build();
        return new MySqlFLockerEngine(pool);
    }
}
