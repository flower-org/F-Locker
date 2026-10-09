package com.flocker.mysql;

import com.flocker.core.FLock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MySqlFResourceTest {

    @Test
    public void exposesIdParentsDescendantsAndVersion() {
        MySqlFResource entity = new MySqlFResource(
                7L, List.of(1L, 2L), List.of(8L, 9L), new MySqlFLock(42L), 3L);

        assertEquals(7L, entity.id());
        assertEquals(List.of(1L, 2L), entity.parentIds());
        assertEquals(List.of(8L, 9L), entity.descendantIds());
        assertEquals(3L, entity.version());

        FLock<?> lock = entity.lock();
        assertNotNull(lock);
        assertEquals(42L, lock.id());
    }

    @Test
    public void unlockedEntityHasNullLock() {
        MySqlFResource entity = new MySqlFResource(1L, List.of(), List.of(), null, 0L);
        assertNull(entity.lock());
    }

    @Test
    public void relationListsAreUnmodifiable() {
        MySqlFResource entity = new MySqlFResource(1L, List.of(2L), List.of(3L), null, 0L);
        assertTrue(throwsUnsupported(() -> entity.parentIds().add(99L)));
        assertTrue(throwsUnsupported(() -> entity.descendantIds().add(99L)));
    }

    private static boolean throwsUnsupported(Runnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (UnsupportedOperationException expected) {
            return true;
        }
    }
}
