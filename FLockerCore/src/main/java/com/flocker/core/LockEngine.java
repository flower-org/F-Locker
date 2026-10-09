package com.flocker.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LockEngine<ENTITY_ID, LOCKER_ID> {
    CompletableFuture<FEntity<ENTITY_ID>> getEntity(ENTITY_ID entityId);
    CompletableFuture<List<FEntity<ENTITY_ID>>> getEntities(List<ENTITY_ID> entityIds);

    CompletableFuture<List<FEntity<ENTITY_ID>>> lockEntity(ENTITY_ID entityId, LOCKER_ID lockerId);
    CompletableFuture<List<FEntity<ENTITY_ID>>> unlockEntity(ENTITY_ID entityId, LOCKER_ID lockerId);
}
