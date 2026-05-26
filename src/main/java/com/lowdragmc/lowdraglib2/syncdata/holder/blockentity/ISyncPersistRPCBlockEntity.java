package com.lowdragmc.lowdraglib2.syncdata.holder.blockentity;

import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import appeng.api.inventories.InternalInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public interface ISyncPersistRPCBlockEntity extends ISyncMangedHolder, com.lowdragmc.lowdraglib.gui.modular.IUIHolder {
    default IManagedStorage getRootStorage() {
        return null;
    }

    @Override
    default com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(Player player) {
        List<InventorySection> inventories = findInternalInventories(this);
        List<FluidSection> fluids = findFluidSections(this);
        ProgressSection progress = findProgressSection(this);
        List<StatusRow> statusRows = findStatusRows(this);
        int machineRows = inventories.stream()
            .mapToInt(section -> section.inventory().size())
            .filter(size -> size > 0)
            .map(size -> (int) Math.ceil(size / 9.0))
            .sum();
        int width = 194;
        int machineHeight = 0;
        if (!fluids.isEmpty()) {
            machineHeight += 68;
        }
        if (progress != null) {
            machineHeight += 28;
        }
        if (!statusRows.isEmpty()) {
            machineHeight += 14 + statusRows.size() * 10;
        }
        if (machineRows > 0) {
            machineHeight += machineRows * 18 + inventories.size() * 18;
        } else if (fluids.isEmpty() && progress == null && statusRows.isEmpty()) {
            machineHeight += 10;
        }
        int height = 28 + machineHeight + 94;

        var ui = new com.lowdragmc.lowdraglib.gui.modular.ModularUI(width, height, this, player)
            .background(ecoPanelTexture());

        Component title = Component.translatable("gui.neoecoae.migration_ui.title");
        if (this instanceof BlockEntity blockEntity) {
            title = blockEntity.getBlockState().getBlock().getName();
        }

        ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(8, 8, title).setTextColor(0xFFFFFFFF));

        int y = 24;
        if (progress != null) {
            ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(8, y, Component.literal("Progress")).setTextColor(0xFFFFFFFF));
            ui.widget(new com.lowdragmc.lowdraglib.gui.widget.ProgressWidget(
                () -> readProgress(progress),
                76,
                y - 2,
                6,
                18
            )
                .setProgressTexture(ecoTexture("textures/gui/bar_container.png"), ecoTexture("textures/gui/bar.png"))
                .setFillDirection(com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection.DOWN_TO_UP)
                .setDynamicHoverTips(value -> "%d%%".formatted((int) Math.round(value * 100))));
            y += 28;
        }

        if (!fluids.isEmpty()) {
            ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(8, y, Component.literal("Fluids")).setTextColor(0xFFFFFFFF));
            y += 12;
            int tankX = 8;
            for (FluidSection fluid : fluids) {
                var transfer = new com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper(fluid.handler());
                for (int tank = 0; tank < fluid.handler().getTanks(); tank++) {
                    ui.widget(new com.lowdragmc.lowdraglib.gui.widget.TankWidget(
                        transfer,
                        tank,
                        tankX,
                        y,
                        18,
                        54,
                        true,
                        true
                    ).setShowAmount(true).setBackground(ecoTexture("textures/gui/slot.png")));
                    tankX += 24;
                }
            }
            y += 58;
        }

        if (!statusRows.isEmpty()) {
            ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(8, y, Component.literal("Machine Status")).setTextColor(0xFFFFFFFF));
            y += 12;
            for (StatusRow row : statusRows) {
                ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(
                    8,
                    y,
                    () -> row.label() + ": " + readFieldValue(row.field(), row.holder())
                ).setTextColor(0xFFB0B0B0));
                y += 10;
            }
            y += 4;
        }

        if (!inventories.isEmpty()) {
            for (InventorySection section : inventories) {
                InternalInventory inventory = section.inventory();
                if (inventory.size() <= 0) {
                    continue;
                }
                Container container = inventory.toContainer();
                int rows = (int) Math.ceil(inventory.size() / (double) section.columns());
                ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(8, y, section.label()).setTextColor(0xFFFFFFFF));
                y += 12;
                addInventorySlots(ui, container, inventory.size(), 8, y, section.columns(), section.canPut(), section.canTake());
                y += rows * 18 + 6;
            }
        }

        ui.widget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(8, y, Component.translatable("container.inventory")).setTextColor(0xFFFFFFFF));
        addPlayerInventorySlots(ui, player, 8, y + 12);
        return ui;
    }

    private static void addInventorySlots(
        com.lowdragmc.lowdraglib.gui.modular.ModularUI ui,
        Container container,
        int size,
        int x,
        int y,
        int columns,
        boolean canPut,
        boolean canTake
    ) {
        for (int slot = 0; slot < size; slot++) {
            int col = slot % columns;
            int row = slot / columns;
            ui.widget(new com.lowdragmc.lowdraglib.gui.widget.SlotWidget(
                container,
                slot,
                x + col * 18,
                y + row * 18,
                canPut,
                canTake
            ).setBackgroundTexture(ecoTexture("textures/gui/slot.png")));
        }
    }

    private static void addPlayerInventorySlots(
        com.lowdragmc.lowdraglib.gui.modular.ModularUI ui,
        Player player,
        int x,
        int y
    ) {
        Container inventory = player.getInventory();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = col + (row + 1) * 9;
                ui.widget(new com.lowdragmc.lowdraglib.gui.widget.SlotWidget(
                    inventory,
                    slot,
                    x + col * 18,
                    y + row * 18,
                    true,
                    true
                ).setLocationInfo(true, false).setBackgroundTexture(ecoTexture("textures/gui/slot.png")));
            }
        }
        for (int col = 0; col < 9; col++) {
            ui.widget(new com.lowdragmc.lowdraglib.gui.widget.SlotWidget(
                inventory,
                col,
                x + col * 18,
                y + 58,
                true,
                true
            ).setLocationInfo(true, true).setBackgroundTexture(ecoTexture("textures/gui/slot.png")));
        }
    }

    private static List<InventorySection> findInternalInventories(Object holder) {
        List<InventorySection> result = new ArrayList<>();
        addInventorySection(result, holder, "getTerminalPatternInventory", Component.literal("Patterns"), true, true, 9);

        InternalInventory input = getInventory(holder, "getInput");
        InternalInventory output = getInventory(holder, "getOutput");
        if (input != null || output != null) {
            if (input != null) {
                addInventorySection(result, new InventorySection(Component.literal("Input"), input, true, true, 9));
            }
            if (output != null) {
                addInventorySection(result, new InventorySection(Component.literal("Output"), output, false, true, 1));
            }
        } else if (result.isEmpty()) {
            addInventorySection(result, holder, "getInternalInventory", Component.literal("Inventory"), true, true, 9);
        }

        addInventorySection(result, holder, "getUpgrades", Component.literal("Upgrades"), true, true, 4);
        return result;
    }

    private static List<StatusRow> findStatusRows(Object holder) {
        List<StatusRow> result = new ArrayList<>();
        Class<?> type = holder.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!isPortableStatusField(field)) {
                    continue;
                }
                field.setAccessible(true);
                result.add(new StatusRow(holder, field, humanize(field.getName())));
            }
            type = type.getSuperclass();
        }
        return result;
    }

    private static boolean isPortableStatusField(Field field) {
        if (!hasAnnotation(field, "DescSynced") && !hasAnnotation(field, "Persisted")) {
            return false;
        }
        Class<?> type = field.getType();
        return type.isPrimitive()
            || Number.class.isAssignableFrom(type)
            || type == Boolean.class
            || type == String.class
            || type.isEnum()
            || type.isArray();
    }

    private static boolean hasAnnotation(Field field, String simpleName) {
        for (var annotation : field.getDeclaredAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static String readFieldValue(Field field, Object holder) {
        try {
            Object value = field.get(holder);
            if (value == null) {
                return "-";
            }
            if (value instanceof long[] values) {
                return summarize(values);
            }
            if (value instanceof int[] values) {
                return summarize(values);
            }
            if (value instanceof boolean[] values) {
                return summarize(values);
            }
            return String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return "-";
        }
    }

    private static String summarize(long[] values) {
        if (values.length == 0) return "[]";
        long total = 0;
        for (long value : values) {
            total += value;
        }
        return String.valueOf(total);
    }

    private static String summarize(int[] values) {
        if (values.length == 0) return "[]";
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return String.valueOf(total);
    }

    private static String summarize(boolean[] values) {
        int total = 0;
        for (boolean value : values) {
            if (value) total++;
        }
        return total + "/" + values.length;
    }

    private static String humanize(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return result.toString();
    }

    private static ProgressSection findProgressSection(Object holder) {
        Method currentMethod = findNoArgMethod(holder, "getProcessingTime");
        Method maxMethod = findNoArgMethod(holder, "getMaxProcessingTime");
        if (currentMethod == null || maxMethod == null) {
            return null;
        }
        return new ProgressSection(holder, currentMethod, maxMethod);
    }

    private static Method findNoArgMethod(Object holder, String methodName) {
        try {
            return holder.getClass().getMethod(methodName);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static double readProgress(ProgressSection progress) {
        try {
            Object currentValue = progress.currentMethod().invoke(progress.holder());
            Object maxValue = progress.maxMethod().invoke(progress.holder());
            if (currentValue instanceof Number current && maxValue instanceof Number max && max.doubleValue() > 0) {
                return Math.max(0, Math.min(1, current.doubleValue() / max.doubleValue()));
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional compatibility hook.
        }
        return 0;
    }

    private static List<FluidSection> findFluidSections(Object holder) {
        List<FluidSection> result = new ArrayList<>();
        for (String methodName : List.of("getFluidCombined", "getFluidHandler")) {
            try {
                Method method = holder.getClass().getMethod(methodName);
                Object value = method.invoke(holder);
                if (value instanceof IFluidHandler handler && handler.getTanks() > 0) {
                    result.add(new FluidSection(handler));
                }
            } catch (ReflectiveOperationException ignored) {
                // Optional compatibility hook.
            }
        }
        return result;
    }

    private static void addInventorySection(
        List<InventorySection> result,
        Object holder,
        String methodName,
        Component label,
        boolean canPut,
        boolean canTake,
        int columns
    ) {
        InternalInventory inventory = getInventory(holder, methodName);
        if (inventory != null) {
            addInventorySection(result, new InventorySection(label, inventory, canPut, canTake, columns));
        }
    }

    private static InternalInventory getInventory(Object holder, String methodName) {
        try {
            Method method = holder.getClass().getMethod(methodName);
            Object value = method.invoke(holder);
            if (value instanceof InternalInventory inventory && inventory.size() > 0) {
                return inventory;
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional compatibility hook.
        }
        return null;
    }

    private static void addInventorySection(List<InventorySection> result, InventorySection section) {
        for (InventorySection existing : result) {
            if (existing.inventory() == section.inventory()) {
                return;
            }
        }
        result.add(section);
    }

    record InventorySection(Component label, InternalInventory inventory, boolean canPut, boolean canTake, int columns) {
    }

    record FluidSection(IFluidHandler handler) {
    }

    record ProgressSection(Object holder, Method currentMethod, Method maxMethod) {
    }

    record StatusRow(Object holder, Field field, String label) {
    }

    private static com.lowdragmc.lowdraglib.gui.texture.ResourceTexture ecoTexture(String path) {
        return new com.lowdragmc.lowdraglib.gui.texture.ResourceTexture("neoecoae:" + path);
    }

    private static com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture ecoPanelTexture() {
        return new com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture(
            "neoecoae:textures/gui/background.png",
            16,
            16,
            4,
            4
        );
    }

    @Override
    default boolean isInvalid() {
        return this instanceof BlockEntity blockEntity && blockEntity.isRemoved();
    }

    @Override
    default boolean isRemote() {
        return this instanceof BlockEntity blockEntity
            && blockEntity.getLevel() != null
            && blockEntity.getLevel().isClientSide();
    }

    @Override
    default void markAsDirty() {
        if (this instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
    }
}
