package com.flocker.core;

import javax.annotation.Nullable;
import java.util.List;

public interface FEntity<ENTITY_ID> {
    ENTITY_ID id();

    @Nullable List<ENTITY_ID> parentIds();
    @Nullable List<ENTITY_ID> descendantIds();

    @Nullable FLocker<?> lock();
}
