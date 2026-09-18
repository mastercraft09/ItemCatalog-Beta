package com.mastercraft.itemcatalog.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Small helper around java.lang.reflect used by every provider in
 * provider/impl. Every method returns an {@link Optional} (or in the case
 * of {@link #call} throws a checked-free {@link ReflectionException}) so
 * providers can chain lookups with a single try/catch and never risk
 * bubbling a NoClassDefFoundError up into the core plugin.
 */
public final class ReflectionUtil {

    private ReflectionUtil() {}

    public static class ReflectionException extends RuntimeException {
        public ReflectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Optional<Class<?>> findClass(String fqcn) {
        try {
            return Optional.of(Class.forName(fqcn));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public static boolean classExists(String fqcn) {
        return findClass(fqcn).isPresent();
    }

    public static Object call(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> clazz = (target instanceof Class) ? (Class<?>) target : target.getClass();
            Method m = findMethod(clazz, methodName, paramTypes);
            if (m == null) {
                throw new NoSuchMethodException(methodName + " on " + clazz.getName());
            }
            m.setAccessible(true);
            Object invokeTarget = (target instanceof Class) ? null : target;
            return m.invoke(invokeTarget, args);
        } catch (InvocationTargetException e) {
            throw new ReflectionException("Failed invoking " + methodName, e.getCause() != null ? e.getCause() : e);
        } catch (Throwable t) {
            throw new ReflectionException("Failed invoking " + methodName, t);
        }
    }

    /** Calls a no-arg method. Convenience overload. */
    public static Object call(Object target, String methodName) {
        return call(target, methodName, new Class<?>[0]);
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>[] paramTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                // also probe interfaces since some APIs expose methods only there
                for (Class<?> iface : current.getInterfaces()) {
                    try {
                        return iface.getMethod(name, paramTypes);
                    } catch (NoSuchMethodException ignored2) {
                        // keep looking
                    }
                }
                current = current.getSuperclass();
            }
        }
        return null;
    }

    public static Object getField(Object target, String fieldName) {
        try {
            Class<?> clazz = (target instanceof Class) ? (Class<?>) target : target.getClass();
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(target instanceof Class ? null : target);
        } catch (Throwable t) {
            throw new ReflectionException("Failed reading field " + fieldName, t);
        }
    }

    public static Object getStatic(String fqcn, String fieldName) {
        Class<?> clazz = findClass(fqcn).orElseThrow(() -> new ReflectionException("Class not found: " + fqcn, null));
        return getField(clazz, fieldName);
    }

    /**
     * Best-effort call that never throws: tries each method name in order
     * (no-arg) and returns the first one that succeeds. Used to stay
     * compatible across API versions of third-party plugins where a
     * method may have been renamed (e.g. getTier() vs getRarity()).
     */
    public static Optional<Object> tryCallAny(Object target, String... candidateMethodNames) {
        if (target == null) return Optional.empty();
        for (String name : candidateMethodNames) {
            try {
                Object result = call(target, name);
                if (result != null) return Optional.of(result);
            } catch (Throwable ignored) {
                // try next candidate
            }
        }
        return Optional.empty();
    }

    /** Same as {@link #tryCallAny(Object, String...)} but with typed parameters for a single candidate. */
    public static Optional<Object> tryCall(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            return Optional.ofNullable(call(target, methodName, paramTypes, args));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
