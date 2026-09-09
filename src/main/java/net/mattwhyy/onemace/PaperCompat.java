package net.mattwhyy.onemace;

import org.bukkit.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class PaperCompat {
    private static final Method GET_REMOVAL_REASON = findMethod(Entity.class, "getRemovalReason");

    private PaperCompat() {
    }

    static boolean isDestructiveRemoval(Entity entity) {
        if (entity == null || GET_REMOVAL_REASON == null) return false;

        try {
            Object reason = GET_REMOVAL_REASON.invoke(entity);
            if (reason == null) return false;

            Method shouldDestroy = findMethod(reason.getClass(), "shouldDestroy");
            if (shouldDestroy == null) return false;

            return Boolean.TRUE.equals(shouldDestroy.invoke(reason));
        } catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
            return false;
        }
    }

    static boolean supportsRemovalReason() {
        return GET_REMOVAL_REASON != null;
    }

    private static Method findMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }
}
