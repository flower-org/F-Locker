package com.flocker.core;

import javax.annotation.Nullable;
import java.util.List;

public interface FResource<RESOURCE_ID> {
    RESOURCE_ID id();

    @Nullable List<RESOURCE_ID> parentIds();
    @Nullable List<RESOURCE_ID> descendantIds();

    @Nullable
    FLock<?> lock();
}
