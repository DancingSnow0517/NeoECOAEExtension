package cn.dancingsnow.neoecoae.gui;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public final class StorageHostHugeStackList extends UIElement implements IBindable<CompoundTag> {
    public record Entry(AEKey key, String amount) {
    }

    private static final String NBT_STACKS = "stacks";
    private static final String NBT_KEY = "key";
    private static final String NBT_AMOUNT = "amount";
    private static final int MAX_SYNCED_STACKS = 128;
    private static final int MAX_SYNC_BYTES = 32_000;
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final float TEXT_SCALE = 0.72F;
    private static final float SCROLL_STEP = 20.0F;
    private static final double SCROLL_RESPONSE_MS = 75.0D;
    private static final BigInteger TWO_LINE_THRESHOLD = BigInteger.valueOf(1024L)
        .pow(6)
        .multiply(BigInteger.valueOf(92L))
        .add(BigInteger.valueOf(9L))
        .divide(BigInteger.TEN);

    private final Supplier<HolderLookup.Provider> registries;
    private final Supplier<? extends Collection<Entry>> entries;
    private final int panelHeight;
    private List<Entry> syncedEntries = List.of();
    private CompoundTag syncedTag = new CompoundTag();
    private float scrollPixels;
    private float targetScrollPixels;
    private long lastFrameNanos;

    StorageHostHugeStackList(
        Supplier<HolderLookup.Provider> registries,
        Supplier<? extends Collection<Entry>> entries,
        int width,
        int height
    ) {
        this.registries = registries;
        this.entries = entries;
        this.panelHeight = height;
        layout(layout -> layout.width(width).height(height));
        bind(DataBindingBuilder.create(
            () -> writeEntries(this.registries.get(), this.entries.get()),
            ignored -> {
            }
        ).syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            float max = maxScrollPixels();
            if (max <= 0.0F) {
                scrollPixels = 0.0F;
                targetScrollPixels = 0.0F;
                return;
            }
            targetScrollPixels = Math.max(0.0F, Math.min(
                max,
                targetScrollPixels + (event.deltaY < 0 ? SCROLL_STEP : -SCROLL_STEP)
            ));
            event.stopImmediatePropagation();
        });
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            Entry entry = entryAt(event.y);
            if (entry != null) {
                List<Component> lines = new ArrayList<>();
                lines.add(entry.key().getDisplayName());
                lines.addAll(StorageHostText.exactAmountTooltip(parseAmount(entry.amount()), StorageHostText.USED));
                event.hoverTooltips = HoverTooltips.empty().append(lines.toArray(Component[]::new));
            }
        });
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        super.drawContents(guiContext);
        updateSmoothScroll();
        if (syncedEntries.isEmpty()) {
            return;
        }

        float x = getPositionX();
        float y = getPositionY();
        guiContext.graphics.enableScissor(
            Math.round(x),
            Math.round(y),
            Math.round(x + getSizeWidth()),
            Math.round(y + getSizeHeight())
        );
        int firstIndex = Math.max(0, (int)Math.floor(scrollPixels / ROW_HEIGHT));
        float firstY = y - (scrollPixels - firstIndex * ROW_HEIGHT);
        int visible = Math.min(syncedEntries.size() - firstIndex, visibleRows() + 1);
        for (int row = 0; row < visible; row++) {
            drawRow(guiContext, syncedEntries.get(firstIndex + row), x, firstY + row * ROW_HEIGHT);
        }
        guiContext.graphics.disableScissor();
        drawScrollbar(guiContext, x, y);
    }

    @Override
    public CompoundTag getValue() {
        return syncedTag.copy();
    }

    @Override
    public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
        syncedTag = value == null ? new CompoundTag() : value.copy();
        syncedEntries = readEntries(registries.get(), syncedTag);
        float max = maxScrollPixels();
        targetScrollPixels = Math.max(0.0F, Math.min(max, targetScrollPixels));
        scrollPixels = Math.max(0.0F, Math.min(max, scrollPixels));
        return this;
    }

    private void drawRow(GUIContext guiContext, Entry entry, float x, float y) {
        if (entry.key() instanceof AEFluidKey fluidKey) {
            FluidStack fluid = fluidKey.toStack(1);
            DrawerHelper.drawFluidForGui(guiContext.graphics, fluid, x, y + 1, ICON_SIZE, ICON_SIZE, -1);
        } else {
            ItemStack displayStack = entry.key().wrapForDisplayOrFilter();
            DrawerHelper.drawItemStack(guiContext.graphics, displayStack, Math.round(x), Math.round(y + 1), -1, null);
        }

        Font font = Minecraft.getInstance().font;
        BigInteger value = parseAmount(entry.amount());
        boolean showName = value.compareTo(TWO_LINE_THRESHOLD) >= 0;
        int availableWidth = Math.max(1, Math.round((getSizeWidth() - ICON_SIZE - 7) / TEXT_SCALE));
        String amount = StorageHostText.hugeStackAmount(value);
        if (font.width(amount) > availableWidth) {
            amount = StorageHostText.fitHugeAmount(value, availableWidth, font::width);
        }
        guiContext.graphics.pose().pushPose();
        guiContext.graphics.pose().translate(x + ICON_SIZE + 3, y + (showName ? 1 : 5), 0);
        guiContext.graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        if (showName) {
            String name = ellipsize(font, entry.key().getDisplayName().getString(), availableWidth);
            guiContext.graphics.drawString(font, name, 0, 0, 0x61AFEF, false);
            guiContext.graphics.drawString(font, amount, 0, 10, StorageHostText.USED, false);
        } else {
            guiContext.graphics.drawString(font, amount, 0, 0, StorageHostText.USED, false);
        }
        guiContext.graphics.pose().popPose();
    }

    private void drawScrollbar(GUIContext guiContext, float x, float y) {
        float max = maxScrollPixels();
        if (max <= 0.0F) {
            return;
        }
        float trackX = x + getSizeWidth() - 2;
        float trackHeight = getSizeHeight();
        float thumbHeight = Math.max(10, trackHeight * trackHeight / (syncedEntries.size() * ROW_HEIGHT));
        float thumbY = y + (trackHeight - thumbHeight) * scrollPixels / max;
        guiContext.graphics.fill(Math.round(trackX), Math.round(y), Math.round(trackX + 2), Math.round(y + trackHeight), 0xAA17141E);
        guiContext.graphics.fill(Math.round(trackX), Math.round(thumbY), Math.round(trackX + 2), Math.round(thumbY + thumbHeight), 0xFF8377FF);
    }

    @Nullable
    private Entry entryAt(double mouseY) {
        float localY = (float)mouseY - getPositionY();
        if (localY < 0.0F || localY >= panelHeight) {
            return null;
        }
        int index = (int)Math.floor((localY + scrollPixels) / ROW_HEIGHT);
        return index >= 0 && index < syncedEntries.size() ? syncedEntries.get(index) : null;
    }

    private int visibleRows() {
        return Math.max(1, panelHeight / ROW_HEIGHT);
    }

    private float maxScrollPixels() {
        return Math.max(0.0F, syncedEntries.size() * ROW_HEIGHT - panelHeight);
    }

    private void updateSmoothScroll() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
        }
        double elapsedMs = Math.min(100.0D, (now - lastFrameNanos) / 1_000_000.0D);
        lastFrameNanos = now;
        float max = maxScrollPixels();
        targetScrollPixels = Math.max(0.0F, Math.min(max, targetScrollPixels));
        double factor = 1.0D - Math.exp(-elapsedMs / SCROLL_RESPONSE_MS);
        scrollPixels += (targetScrollPixels - scrollPixels) * (float)factor;
        if (Math.abs(targetScrollPixels - scrollPixels) < 0.05F) {
            scrollPixels = targetScrollPixels;
        }
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width(suffix))) + suffix;
    }

    private static CompoundTag writeEntries(HolderLookup.Provider registries, Collection<Entry> source) {
        CompoundTag result = new CompoundTag();
        ListTag stacks = new ListTag();
        int remainingBytes = MAX_SYNC_BYTES;
        if (source != null) {
            for (Entry entry : source) {
                if (stacks.size() >= MAX_SYNCED_STACKS || entry == null || entry.key() == null) {
                    break;
                }
                CompoundTag stack = new CompoundTag();
                stack.put(NBT_KEY, entry.key().toTagGeneric(registries));
                stack.putString(NBT_AMOUNT, normalizeAmount(entry.amount()));
                int size = Math.toIntExact(Math.min(Integer.MAX_VALUE, stack.sizeInBytes()));
                if (size > remainingBytes) {
                    break;
                }
                stacks.add(stack);
                remainingBytes -= size;
            }
        }
        result.put(NBT_STACKS, stacks);
        return result;
    }

    private static List<Entry> readEntries(HolderLookup.Provider registries, CompoundTag tag) {
        ListTag stacks = tag.getList(NBT_STACKS, Tag.TAG_COMPOUND);
        List<Entry> result = new ArrayList<>(Math.min(stacks.size(), MAX_SYNCED_STACKS));
        for (int i = 0; i < stacks.size() && result.size() < MAX_SYNCED_STACKS; i++) {
            CompoundTag stack = stacks.getCompound(i);
            AEKey key = AEKey.fromTagGeneric(registries, stack.getCompound(NBT_KEY));
            if (key != null) {
                result.add(new Entry(key, normalizeAmount(stack.getString(NBT_AMOUNT))));
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeAmount(String amount) {
        BigInteger parsed = parseAmount(amount);
        return parsed.signum() < 0 ? "0" : parsed.toString();
    }

    private static BigInteger parseAmount(String amount) {
        try {
            return amount == null || amount.isBlank() ? BigInteger.ZERO : new BigInteger(amount);
        } catch (NumberFormatException ignored) {
            return BigInteger.ZERO;
        }
    }
}
