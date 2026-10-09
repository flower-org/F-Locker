package com.flocker.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FLockerEngine<RESOURCE_ID, LOCK_ID> {
    CompletableFuture<FResource<RESOURCE_ID>> getResource(RESOURCE_ID resourceId);
    CompletableFuture<List<FResource<RESOURCE_ID>>> getResources(List<RESOURCE_ID> resourceIds);

    CompletableFuture<List<FResource<RESOURCE_ID>>> lockResource(RESOURCE_ID resourceId, LOCK_ID lockId);
    CompletableFuture<List<FResource<RESOURCE_ID>>> unlockResource(RESOURCE_ID resourceId, LOCK_ID lockId);
    CompletableFuture<List<FResource<RESOURCE_ID>>> unlockAll(LOCK_ID lockId);

    CompletableFuture<LOCK_ID> createLock();
    CompletableFuture<LOCK_ID> deleteLock(LOCK_ID lockId);
}
