package com.flocker.mysql;

/**
 * Thrown when a resource (or any of its descendants) cannot be locked because it
 * is already locked.
 */
public class LockConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final long resourceId;
    private final long heldByLockId;

    public LockConflictException(long resourceId, long heldByLockId) {
        super("Resource " + resourceId + " is already locked. LockId " + heldByLockId);
        this.resourceId = resourceId;
        this.heldByLockId = heldByLockId;
    }

    public long resourceId() {
        return resourceId;
    }

    public long heldByLockId() {
        return heldByLockId;
    }
}
