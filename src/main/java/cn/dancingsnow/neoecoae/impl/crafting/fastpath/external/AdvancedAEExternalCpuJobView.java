package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Reflection-backed view keeps AdvancedAE optional on Forge 1.20.1. */
public final class AdvancedAEExternalCpuJobView implements ECOExternalCpuJobView {
    private final Object logic;
    private final Object job;
    private final ListCraftingInventory inventory;
    private final Object cpu;

    public AdvancedAEExternalCpuJobView(Object logic) {
        this.logic = logic;
        this.job = readField(logic, "job");
        this.inventory = (ListCraftingInventory) readField(logic, "inventory");
        this.cpu = readField(logic, "cpu");
    }

    public boolean hasJob() {
        return job != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Task> tasks() {
        Map<IPatternDetails, Object> tasks = (Map<IPatternDetails, Object>) readField(job, "tasks");
        return new TaskIterator(tasks.entrySet().iterator());
    }

    @Override
    public ListCraftingInventory inventory() {
        return inventory;
    }

    @Override
    public ListCraftingInventory waitingFor() {
        return (ListCraftingInventory) readField(job, "waitingFor");
    }

    @Override
    public UUID craftingId() {
        return ((CraftingLink) readField(job, "link")).getCraftingID();
    }

    @Override
    public void addContainerMaxItems(long amount, AEKeyType keyType) {
        invoke(
                readField(job, "timeTracker"),
                "addMaxItems",
                new Class<?>[] {long.class, AEKeyType.class},
                amount,
                keyType);
    }

    @Override
    public void markDirty() {
        invoke(cpu, "markDirty", new Class<?>[0]);
    }

    public long insert(AEKey what, long amount, Actionable type) {
        return (long)
                invoke(logic, "insert", new Class<?>[] {AEKey.class, long.class, Actionable.class}, what, amount, type);
    }

    private static Object readField(Object owner, String name) {
        if (owner == null) {
            return null;
        }
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to read AdvancedAE field " + name, e);
            }
        }
        throw new IllegalStateException("Missing AdvancedAE field " + name);
    }

    private static Object invoke(Object owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        if (owner == null) {
            throw new IllegalStateException("AdvancedAE target is unavailable for " + name);
        }
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method.invoke(owner, arguments);
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to invoke AdvancedAE method " + name, e);
            }
        }
        throw new IllegalStateException("Missing AdvancedAE method " + name);
    }

    private static final class TaskIterator implements Iterator<Task> {
        private final Iterator<Map.Entry<IPatternDetails, Object>> delegate;

        private TaskIterator(Iterator<Map.Entry<IPatternDetails, Object>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Task next() {
            return new TaskEntry(delegate.next());
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }

    private record TaskEntry(Map.Entry<IPatternDetails, Object> entry) implements Task {
        @Override
        public IPatternDetails details() {
            return entry.getKey();
        }

        @Override
        public long remaining() {
            return ((Number) readField(entry.getValue(), "value")).longValue();
        }

        @Override
        public void remaining(long value) {
            Object progress = entry.getValue();
            Class<?> type = progress.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField("value");
                    field.setAccessible(true);
                    field.setLong(progress, value);
                    return;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to update AdvancedAE task progress", e);
                }
            }
            throw new IllegalStateException("Missing AdvancedAE task progress field");
        }
    }
}
