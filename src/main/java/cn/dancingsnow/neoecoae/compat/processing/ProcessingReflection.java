package cn.dancingsnow.neoecoae.compat.processing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Cached optional-mod boundary. Only explicitly named, read-only members are accessed. */
public final class ProcessingReflection {
    private ProcessingReflection() {}
    private static final ClassValue<Map<String, Optional<Field>>> FIELDS = new ClassValue<>() {
        @Override protected Map<String, Optional<Field>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static final ClassValue<Map<String, Optional<Method>>> METHODS = new ClassValue<>() {
        @Override protected Map<String, Optional<Method>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    public static boolean is(Object value, String type) {
        if (value == null) return false;
        for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) if (c.getName().equals(type)) return true;
        return false;
    }

    public static Object field(Object value, String name) {
        if (value == null) return null;
        var field = FIELDS.get(value.getClass()).computeIfAbsent(name, ignored -> {
            for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    var f = c.getDeclaredField(name);
                    return f.trySetAccessible() ? Optional.of(f) : Optional.empty();
                } catch (NoSuchFieldException missing) { /* Try the superclass. */ }
            }
            return Optional.empty();
        });
        try { return field.isEmpty() ? null : field.get().get(value); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static Object call(Object value, String name, Object... args) {
        if (value == null) return null;
        String key = name + (args.length == 0 ? "()" : "(int)");
        var method = METHODS.get(value.getClass()).computeIfAbsent(key, ignored -> {
            for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    var m = c.getDeclaredMethod(name, args.length == 0 ? new Class<?>[0] : new Class<?>[]{int.class});
                    return m.trySetAccessible() ? Optional.of(m) : Optional.empty();
                } catch (NoSuchMethodException missing) { /* Try the superclass. */ }
            }
            // Includes methods supplied by public interfaces (for example output records).
            try { return Optional.of(value.getClass().getMethod(name, args.length == 0 ? new Class<?>[0] : new Class<?>[]{int.class})); }
            catch (NoSuchMethodException missing) { return Optional.empty(); }
        });
        try { return method.isEmpty() ? null : method.get().invoke(value, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static int integer(Object value) { return value instanceof Number n ? n.intValue() : 0; }
    public static long count(Object value) { return value instanceof Number n ? n.longValue() : 0; }
}
