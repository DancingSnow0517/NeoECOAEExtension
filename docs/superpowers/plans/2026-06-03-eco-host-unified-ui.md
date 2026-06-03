# ECO Host Unified UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the storage, computation, and crafting host text panels with one shared modern LowDragLib UI system.

**Architecture:** Build shared Java UI composition helpers and small registered widgets in `cn.dancingsnow.neoecoae.gui.widget`, then have each block entity supply dynamic metric/detail data to those helpers. Keep existing synced fields and multiblock builder behavior intact.

**Tech Stack:** Java 21, NeoForge 1.21.1, LowDragLib2 `UIElement`, `ProgressBar`, `Switch`, `@LDLRegister`, Taffy layout, existing `eco.lss` stylesheet.

---

## File Map

- Create `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostStyles.java`
  - Centralizes text colors, dimensions, ratio helpers, and text style functions.

- Create `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostMetric.java`
  - Registered reusable top metric card.
  - Supports ratio metrics with a progress bar and scalar metrics with an invisible spacer.

- Create `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostWidgets.java`
  - Composition helper for host panel shell, status badge, section titles, tiles, cards, stat lines, and builder footer.
  - Keeps complex layout out of block entities.

- Create `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostSwitchRow.java`
  - Registered reusable row containing a label, optional tooltip, and LowDragLib `Switch`.
  - Ensures switches stay right-aligned and do not wrap.

- Modify `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java`
  - Replace old `ScrollerView` text panel with the shared host panel.
  - Add aggregate type and byte helpers.

- Modify `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/computation/ECOComputationSystemBlockEntity.java`
  - Replace old text panel with the shared host panel.
  - Put CPU storage first, then thread usage, then parallel count.

- Modify `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java`
  - Replace old text panel with the shared host panel.
  - Keep overclock and active cooling controls visible without scrolling.
  - Fix zero-division risk in thread percentage while touching this UI.

- Modify `src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java`
  - Add English source keys for new labels.

- Modify language JSON files:
  - `src/main/resources/assets/neoecoae/lang/zh_cn.json`
  - `src/main/resources/assets/neoecoae/lang/zh_hk.json`
  - `src/main/resources/assets/neoecoae/lang/zh_tw.json`
  - `src/main/resources/assets/neoecoae/lang/lzh.json`
  - Add new keys using the exact translations listed in Task 5 for `zh_cn`; use concise Traditional Chinese equivalents for `zh_hk` and `zh_tw`; use the English values for `lzh` when a classical translation would be unclear.

- Modify `src/main/resources/assets/neoecoae/lss/eco.lss`
  - Add stable classes for the new UI system.

- Keep `output/ui-mockups/eco-host-unified-ui-v2.html`
  - Reference only; do not commit unless explicitly requested.

---

### Task 1: Add Shared Style Constants And Text Helpers

**Files:**
- Create: `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostStyles.java`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Create `ECOHostStyles.java`**

Use this complete file:

```java
package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

public final class ECOHostStyles {
    public static final int PANEL_WIDTH = 460;
    public static final int PANEL_HEIGHT = 380;

    public static final int TEXT = 0x263238;
    public static final int MUTED = 0x627178;
    public static final int SOFT = 0x7d8a91;
    public static final int ACCENT = 0x1f8ea3;
    public static final int GREEN = 0x3c9f68;
    public static final int AMBER = 0xbd8128;
    public static final int RED = 0xbd4b62;

    private ECOHostStyles() {
    }

    public static void titleText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false);
    }

    public static void subtitleText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(MUTED).textShadow(false);
    }

    public static void sectionText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(ACCENT).textShadow(false);
    }

    public static void labelText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(MUTED).textShadow(false);
    }

    public static void valueText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false);
    }

    public static void hintText(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(SOFT).textShadow(false);
    }

    public static float ratio(long used, long total) {
        if (total <= 0) {
            return 0.0f;
        }
        return Math.clamp((float) used / (float) total, 0.0f, 1.0f);
    }

    public static int percent(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.clamp((int) ((double) used / (double) total * 100.0), 0, 100);
    }
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostStyles.java
git commit -m "Add ECO host UI style helpers"
```

---

### Task 2: Add Registered Metric Card Widget

**Files:**
- Create: `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostMetric.java`
- Modify: `src/main/resources/assets/neoecoae/lss/eco.lss`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Create `ECOHostMetric.java`**

Use this complete file:

```java
package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

@LDLRegister(name = "eco-host-metric", group = "neoecoae", registry = "ldlib2:ui_element")
public class ECOHostMetric extends UIElement {
    private static final int WIDTH = 132;
    private static final int HEIGHT = 72;

    public ECOHostMetric() {
        this(
            () -> Component.empty(),
            () -> Component.empty(),
            null
        );
    }

    public ECOHostMetric(Supplier<Component> label, Supplier<Component> value, Supplier<Float> ratio) {
        addClass("eco-host-metric");
        layout(layout -> {
            layout.width(WIDTH);
            layout.height(HEIGHT);
            layout.paddingAll(5);
            layout.gapAll(4);
        });

        addChild(new Label()
            .bindDataSource(SupplierDataSource.of(label))
            .textStyle(ECOHostStyles::labelText)
            .layout(layout -> layout.height(18)));

        addChild(new Label()
            .bindDataSource(SupplierDataSource.of(value))
            .textStyle(ECOHostStyles::valueText)
            .layout(layout -> layout.height(30)));

        if (ratio == null) {
            addChild(new UIElement()
                .addClass("eco-host-metric-spacer")
                .layout(layout -> layout.height(5).widthPercent(100)));
        } else {
            addChild(new ProgressBar()
                .label(labelElement -> labelElement.setText(""))
                .bind(DataBindingBuilder.floatValS2C(ratio::get).build())
                .layout(layout -> layout.height(5).widthPercent(100)));
        }
    }

    public static ECOHostMetric ratio(Supplier<Component> label, Supplier<Component> value, Supplier<Float> ratio) {
        return new ECOHostMetric(label, value, ratio);
    }

    public static ECOHostMetric scalar(Supplier<Component> label, Supplier<Component> value) {
        return new ECOHostMetric(label, value, null);
    }
}
```

- [ ] **Step 2: Add LSS classes**

Append these rules to `src/main/resources/assets/neoecoae/lss/eco.lss`:

```css

// unified ECO host UI
.eco-host-panel {
  background: built-in(ui-eco:BACKGROUND);
}

.eco-host-metric, .eco-host-card, .eco-host-tile {
  background: built-in(ui-eco:INVENTORY_BORDER);
}

.eco-host-metric-spacer {
  background: #00000000;
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds. `ProgressBar` already defaults to the `0..1` range used by `ECOHostStyles.ratio`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostMetric.java src/main/resources/assets/neoecoae/lss/eco.lss
git commit -m "Add ECO host metric widget"
```

---

### Task 3: Add Host Panel Composition Helpers

**Files:**
- Create: `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostWidgets.java`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Create `ECOHostWidgets.java`**

Use this complete file:

```java
package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public final class ECOHostWidgets {
    private ECOHostWidgets() {
    }

    public static UIElement hostPanel(
        Supplier<Component> title,
        Supplier<Component> subtitle,
        Supplier<Component> state,
        List<ECOHostMetric> metrics,
        UIElement details,
        Supplier<Component> footerHint,
        UIElement buildWindow
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(ECOHostStyles.PANEL_WIDTH);
            layout.height(ECOHostStyles.PANEL_HEIGHT);
            layout.paddingAll(10);
            layout.gapAll(7);
        }).addClass("eco-host-panel");

        root.addChild(header(title, subtitle, state));
        root.addChild(metricRow(metrics));
        root.addChild(details.layout(layout -> layout.widthPercent(100)));
        root.addChild(footer(footerHint, buildWindow));
        root.addChild(buildWindow);
        return root;
    }

    public static UIElement header(Supplier<Component> title, Supplier<Component> subtitle, Supplier<Component> state) {
        UIElement header = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(8);
            layout.height(46);
        }).addClass("eco-host-header");

        UIElement titleBox = new UIElement().layout(layout -> {
            layout.gapAll(2);
            layout.width(320);
        });
        titleBox.addChild(new Label().bindDataSource(SupplierDataSource.of(title)).textStyle(ECOHostStyles::titleText));
        titleBox.addChild(new Label().bindDataSource(SupplierDataSource.of(subtitle)).textStyle(ECOHostStyles::subtitleText));
        header.addChild(titleBox);

        header.addChild(new Label()
            .bindDataSource(SupplierDataSource.of(state))
            .textStyle(ECOHostStyles::sectionText)
            .layout(layout -> layout.width(82).height(22).paddingAll(3))
            .addClass("eco-host-status"));

        return header;
    }

    public static UIElement metricRow(List<ECOHostMetric> metrics) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(7);
            layout.height(72);
        }).addClass("eco-host-metrics");
        metrics.forEach(row::addChild);
        return row;
    }

    public static UIElement detailArea(boolean scroll) {
        if (!scroll) {
            return new UIElement().layout(layout -> {
                layout.gapAll(5);
                layout.height(209);
            }).addClass("eco-host-details");
        }
        ScrollerView scroller = new ScrollerView().viewContainer(view -> view.getLayout().gapAll(5));
        scroller.layout(layout -> layout.height(209));
        scroller.addClass("eco-host-details");
        return scroller;
    }

    public static Label sectionTitle(String key) {
        return new Label()
            .setText(Component.translatable(key))
            .textStyle(ECOHostStyles::sectionText)
            .layout(layout -> layout.height(14));
    }

    public static UIElement card() {
        return new UIElement().layout(layout -> {
            layout.paddingAll(6);
            layout.gapAll(5);
        }).addClass("eco-host-card");
    }

    public static UIElement tile(String key, Supplier<Component> value) {
        UIElement tile = new UIElement().layout(layout -> {
            layout.width(100);
            layout.height(40);
            layout.paddingAll(5);
            layout.gapAll(2);
        }).addClass("eco-host-tile");
        tile.addChild(new Label().setText(Component.translatable(key)).textStyle(ECOHostStyles::labelText));
        tile.addChild(new Label().bindDataSource(SupplierDataSource.of(value)).textStyle(ECOHostStyles::valueText));
        return tile;
    }

    public static UIElement tileRow(List<UIElement> tiles) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(5);
            layout.height(42);
        });
        tiles.forEach(row::addChild);
        return row;
    }

    public static UIElement statLine(String key, Supplier<Component> value, Supplier<Float> ratio) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(5);
            layout.height(12);
        }).addClass("eco-host-stat-line");
        row.addChild(new Label().setText(Component.translatable(key)).textStyle(ECOHostStyles::labelText).layout(layout -> layout.width(54)));
        row.addChild(new ProgressBar()
            .label(label -> label.setText(""))
            .bind(DataBindingBuilder.floatValS2C(ratio::get).build())
            .layout(layout -> layout.width(210).height(5)));
        row.addChild(new Label().bindDataSource(SupplierDataSource.of(value)).textStyle(ECOHostStyles::valueText).layout(layout -> layout.width(82)));
        return row;
    }

    public static UIElement footer(Supplier<Component> hint, UIElement buildWindow) {
        UIElement footer = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(8);
            layout.height(26);
        }).addClass("eco-host-footer");
        footer.addChild(new Label().bindDataSource(SupplierDataSource.of(hint)).textStyle(ECOHostStyles::hintText).layout(layout -> layout.width(390)));
        footer.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        return footer;
    }

    public static void addDetailChild(UIElement details, UIElement child) {
        if (details instanceof ScrollerView scroller) {
            scroller.addScrollViewChild(child);
        } else {
            details.addChild(child);
        }
    }
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds. If `ScrollerView.addClass` return typing causes chaining issues, split it into two statements.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostWidgets.java
git commit -m "Add ECO host panel helpers"
```

---

### Task 4: Add Registered Switch Row Widget

**Files:**
- Create: `src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostSwitchRow.java`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Create `ECOHostSwitchRow.java`**

Use this complete file:

```java
package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@LDLRegister(name = "eco-host-switch-row", group = "neoecoae", registry = "ldlib2:ui_element")
public class ECOHostSwitchRow extends UIElement {
    public ECOHostSwitchRow() {
        this(
            Component.empty(),
            Component.empty(),
            () -> false,
            ignored -> {
            }
        );
    }

    public ECOHostSwitchRow(Component label, Component tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addClass("eco-host-switch-row");
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
            layout.height(26);
        });

        addChild(new TextElement()
            .setText(label)
            .textStyle(ECOHostStyles::valueText)
            .layout(layout -> layout.width(170)));

        Switch toggle = new Switch()
            .bind(DataBindingBuilder.bool(getter::get, setter::accept).build())
            .layout(layout -> layout.width(34).height(18));

        if (!tooltip.equals(Component.empty())) {
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(tooltip),
                null,
                null,
                null
            ));
        }

        addChild(toggle);
    }
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/gui/widget/ECOHostSwitchRow.java
git commit -m "Add ECO host switch row widget"
```

---

### Task 5: Add Localization Keys

**Files:**
- Modify: `src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java`
- Modify: `src/main/resources/assets/neoecoae/lang/zh_cn.json`
- Modify: `src/main/resources/assets/neoecoae/lang/zh_hk.json`
- Modify: `src/main/resources/assets/neoecoae/lang/zh_tw.json`
- Modify: `src/main/resources/assets/neoecoae/lang/lzh.json`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Add English datagen keys**

In `GuiLangs.add`, after the existing storage/computation/crafting keys, add:

```java
        provider.add("gui.neoecoae.host.status.online", "ONLINE");
        provider.add("gui.neoecoae.host.status.running", "RUNNING");
        provider.add("gui.neoecoae.host.storage.subtitle", "Storage System Host");
        provider.add("gui.neoecoae.host.computation.subtitle", "Computation System Host");
        provider.add("gui.neoecoae.host.crafting.subtitle", "Crafting System Host");
        provider.add("gui.neoecoae.host.storage.type_usage", "Type Usage");
        provider.add("gui.neoecoae.host.storage.storage_usage", "Storage Usage");
        provider.add("gui.neoecoae.host.storage.energy_buffer", "Energy Buffer");
        provider.add("gui.neoecoae.host.storage.channels", "Storage Channels");
        provider.add("gui.neoecoae.host.storage.footer", "Dynamic by ECOCellType registry; storage channels scroll when expanded.");
        provider.add("gui.neoecoae.host.metric.types", "Types");
        provider.add("gui.neoecoae.host.metric.bytes", "Bytes");
        provider.add("gui.neoecoae.host.computation.cpu_storage", "CPU Storage");
        provider.add("gui.neoecoae.host.computation.thread_usage", "Thread Usage");
        provider.add("gui.neoecoae.host.computation.parallel_count", "Parallel Count");
        provider.add("gui.neoecoae.host.computation.capacity", "Computation Capacity");
        provider.add("gui.neoecoae.host.computation.active_vcpu", "Active vCPU");
        provider.add("gui.neoecoae.host.computation.max_vcpu", "Max vCPU");
        provider.add("gui.neoecoae.host.computation.accelerators", "Accelerators");
        provider.add("gui.neoecoae.host.computation.free_memory", "Free CPU Memory");
        provider.add("gui.neoecoae.host.computation.cpu_pool", "Crafting CPU Pool");
        provider.add("gui.neoecoae.host.computation.cpu_pool_hint", "Threads expose virtual crafting CPUs to the AE network.");
        provider.add("gui.neoecoae.host.computation.footer", "Single-screen computation status.");
        provider.add("gui.neoecoae.host.crafting.working_threads", "Working Threads");
        provider.add("gui.neoecoae.host.crafting.total_parallelism", "Total Parallelism");
        provider.add("gui.neoecoae.host.crafting.max_energy_usage", "Max Energy Usage");
        provider.add("gui.neoecoae.host.crafting.runtime", "Crafting Runtime");
        provider.add("gui.neoecoae.host.crafting.pattern_buses", "Pattern Buses");
        provider.add("gui.neoecoae.host.crafting.parallel_cores", "Parallel Cores");
        provider.add("gui.neoecoae.host.crafting.worker_cores", "Worker Cores");
        provider.add("gui.neoecoae.host.crafting.overflow", "Overflow");
        provider.add("gui.neoecoae.host.crafting.overclock_cooling", "Overclock & Cooling");
        provider.add("gui.neoecoae.host.crafting.overclock_summary", "Theoretical %d, effective %d, coolant max %s.");
        provider.add("gui.neoecoae.host.crafting.coolant", "Coolant");
        provider.add("gui.neoecoae.host.crafting.energy", "Energy");
        provider.add("gui.neoecoae.host.crafting.footer", "Single-screen crafting status and controls.");
```

- [ ] **Step 2: Add `zh_cn.json` keys**

Add these JSON entries, preserving valid comma placement:

```json
  "gui.neoecoae.host.status.online": "在线",
  "gui.neoecoae.host.status.running": "运行中",
  "gui.neoecoae.host.storage.subtitle": "存储子系统主机",
  "gui.neoecoae.host.computation.subtitle": "计算子系统主机",
  "gui.neoecoae.host.crafting.subtitle": "合成子系统主机",
  "gui.neoecoae.host.storage.type_usage": "类型占用",
  "gui.neoecoae.host.storage.storage_usage": "存储占用",
  "gui.neoecoae.host.storage.energy_buffer": "能量缓存",
  "gui.neoecoae.host.storage.channels": "存储通道",
  "gui.neoecoae.host.storage.footer": "根据 ECOCellType 注册表动态生成；通道增多时仅列表滚动。",
  "gui.neoecoae.host.metric.types": "类型",
  "gui.neoecoae.host.metric.bytes": "字节",
  "gui.neoecoae.host.computation.cpu_storage": "CPU 存储",
  "gui.neoecoae.host.computation.thread_usage": "线程占用",
  "gui.neoecoae.host.computation.parallel_count": "并行数",
  "gui.neoecoae.host.computation.capacity": "计算容量",
  "gui.neoecoae.host.computation.active_vcpu": "活动 vCPU",
  "gui.neoecoae.host.computation.max_vcpu": "最大 vCPU",
  "gui.neoecoae.host.computation.accelerators": "加速器",
  "gui.neoecoae.host.computation.free_memory": "空闲 CPU 存储",
  "gui.neoecoae.host.computation.cpu_pool": "合成 CPU 池",
  "gui.neoecoae.host.computation.cpu_pool_hint": "线程会向 AE 网络暴露虚拟合成 CPU。",
  "gui.neoecoae.host.computation.footer": "计算状态单屏展示。",
  "gui.neoecoae.host.crafting.working_threads": "工作线程",
  "gui.neoecoae.host.crafting.total_parallelism": "总并行数",
  "gui.neoecoae.host.crafting.max_energy_usage": "最大能耗",
  "gui.neoecoae.host.crafting.runtime": "合成运行状态",
  "gui.neoecoae.host.crafting.pattern_buses": "样板总线",
  "gui.neoecoae.host.crafting.parallel_cores": "并行核心",
  "gui.neoecoae.host.crafting.worker_cores": "工作核心",
  "gui.neoecoae.host.crafting.overflow": "溢出",
  "gui.neoecoae.host.crafting.overclock_cooling": "超频与冷却",
  "gui.neoecoae.host.crafting.overclock_summary": "理论 %d，生效 %d，冷却最高 %s。",
  "gui.neoecoae.host.crafting.coolant": "冷却液",
  "gui.neoecoae.host.crafting.energy": "能耗",
  "gui.neoecoae.host.crafting.footer": "合成状态与控制单屏展示。"
```

- [ ] **Step 3: Add zh_hk/zh_tw/lzh keys**

Use the same keys. For `zh_hk.json` and `zh_tw.json`, Traditional Chinese text can mirror existing vocabulary. For `lzh.json`, short classical labels are acceptable, but do not omit keys. If a translation is uncertain, use the English datagen value rather than leaving the key missing.

- [ ] **Step 4: Validate JSON**

Run:

```bash
python -m json.tool src/main/resources/assets/neoecoae/lang/zh_cn.json
python -m json.tool src/main/resources/assets/neoecoae/lang/zh_hk.json
python -m json.tool src/main/resources/assets/neoecoae/lang/zh_tw.json
python -m json.tool src/main/resources/assets/neoecoae/lang/lzh.json
```

Expected: each command prints formatted JSON and exits successfully.

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/data/lang/GuiLangs.java src/main/resources/assets/neoecoae/lang/zh_cn.json src/main/resources/assets/neoecoae/lang/zh_hk.json src/main/resources/assets/neoecoae/lang/zh_tw.json src/main/resources/assets/neoecoae/lang/lzh.json
git commit -m "Add ECO host UI localization"
```

---

### Task 6: Replace Storage Host UI

**Files:**
- Modify: `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Add imports**

Add these imports:

```java
import cn.dancingsnow.neoecoae.gui.widget.ECOHostMetric;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
```

Remove unused imports from the old text UI after replacing the method.

- [ ] **Step 2: Replace `createUI` body**

Replace the old text-panel construction inside `createUI` with this implementation:

```java
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        resetStorageInfosIfNeeded();
        UIElement buildWindow = buildPanel(holder);

        UIElement details = ECOHostWidgets.detailArea(true);
        ECOHostWidgets.addDetailChild(details, ECOHostWidgets.sectionTitle("gui.neoecoae.host.storage.channels"));
        NERegistries.CELL_TYPE.stream().forEachOrdered(cellType -> {
            int id = NERegistries.CELL_TYPE.getId(cellType);
            ECOHostWidgets.addDetailChild(details, createStorageChannelCard(cellType, id));
        });

        UIElement root = ECOHostWidgets.hostPanel(
            () -> getItemFromBlockEntity().getDescription(),
            () -> Component.translatable("gui.neoecoae.host.storage.subtitle"),
            () -> Component.translatable("gui.neoecoae.host.status.online"),
            List.of(
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.storage.type_usage"),
                    () -> Component.literal(Tooltips.ofNumber(getTotalUsedTypes()).getString() + " / " + Tooltips.ofNumber(getTotalTypes()).getString()),
                    () -> ECOHostStyles.ratio(getTotalUsedTypes(), getTotalTypes())
                ),
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.storage.storage_usage"),
                    () -> Component.literal(Tooltips.ofBytes(getTotalUsedBytes()).getString() + " / " + Tooltips.ofBytes(getTotalBytes()).getString()),
                    () -> ECOHostStyles.ratio(getTotalUsedBytes(), getTotalBytes())
                ),
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.storage.energy_buffer"),
                    () -> Component.literal(Tooltips.ofNumber(storedEnergy).getString() + " / " + Tooltips.ofNumber(maxEnergy).getString() + "AE"),
                    () -> ECOHostStyles.ratio(storedEnergy, maxEnergy)
                )
            ),
            details,
            () -> Component.translatable("gui.neoecoae.host.storage.footer"),
            buildWindow
        );

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
```

- [ ] **Step 3: Add storage helper methods**

Add these methods near `getArrayValue`:

```java
    private long getTotalUsedTypes() {
        return sumArray(usedTypes);
    }

    private long getTotalTypes() {
        return sumArray(totalTypes);
    }

    private long getTotalUsedBytes() {
        return sumArray(usedBytes);
    }

    private long getTotalBytes() {
        return sumArray(totalBytes);
    }

    private static long sumArray(long[] values) {
        if (values == null) {
            return 0;
        }
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum;
    }

    private UIElement createStorageChannelCard(ECOCellType cellType, int id) {
        UIElement card = ECOHostWidgets.card().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(8);
            layout.height(52);
        });

        UIElement name = new UIElement().layout(layout -> {
            layout.width(145);
            layout.gapAll(2);
        });
        name.addChild(new Label().setText(cellType.desc()).textStyle(ECOHostStyles::valueText));
        name.addChild(new Label().setText(Component.translatable("gui.neoecoae.host.storage.subtitle")).textStyle(ECOHostStyles::hintText));
        card.addChild(name);

        UIElement stats = new UIElement().layout(layout -> layout.gapAll(5).width(265));
        stats.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.metric.types",
            () -> Component.literal(Tooltips.ofNumber(getArrayValue(usedTypes, id)).getString() + " / " + Tooltips.ofNumber(getArrayValue(totalTypes, id)).getString()),
            () -> ECOHostStyles.ratio(getArrayValue(usedTypes, id), getArrayValue(totalTypes, id))
        ));
        stats.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.metric.bytes",
            () -> Component.literal(Tooltips.ofBytes(getArrayValue(usedBytes, id)).getString() + " / " + Tooltips.ofBytes(getArrayValue(totalBytes, id)).getString()),
            () -> ECOHostStyles.ratio(getArrayValue(usedBytes, id), getArrayValue(totalBytes, id))
        ));
        card.addChild(stats);
        return card;
    }
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/blocks/entity/storage/ECOStorageSystemBlockEntity.java
git commit -m "Redesign storage host UI"
```

---

### Task 7: Replace Computation Host UI

**Files:**
- Modify: `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/computation/ECOComputationSystemBlockEntity.java`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Add imports**

Add:

```java
import cn.dancingsnow.neoecoae.gui.widget.ECOHostMetric;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
```

Remove old text-panel imports after replacement.

- [ ] **Step 2: Replace `createUI`**

Use this implementation:

```java
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement buildWindow = buildPanel(holder);
        UIElement details = ECOHostWidgets.detailArea(false);
        details.addChild(ECOHostWidgets.sectionTitle("gui.neoecoae.host.computation.capacity"));
        details.addChild(ECOHostWidgets.tileRow(List.of(
            ECOHostWidgets.tile("gui.neoecoae.host.computation.active_vcpu", () -> Component.literal(String.valueOf(usedThread))),
            ECOHostWidgets.tile("gui.neoecoae.host.computation.max_vcpu", () -> Component.literal(String.valueOf(totalThread))),
            ECOHostWidgets.tile("gui.neoecoae.host.computation.accelerators", () -> Component.literal(String.valueOf(parallelCount))),
            ECOHostWidgets.tile("gui.neoecoae.host.computation.free_memory", () -> Tooltips.ofBytes(Math.max(availableBytes, 0)))
        )));

        UIElement cpuPool = ECOHostWidgets.card();
        cpuPool.addChild(new Label().setText(Component.translatable("gui.neoecoae.host.computation.cpu_pool")).textStyle(ECOHostStyles::valueText));
        cpuPool.addChild(new Label().setText(Component.translatable("gui.neoecoae.host.computation.cpu_pool_hint")).textStyle(ECOHostStyles::hintText));
        cpuPool.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.computation.thread_usage",
            () -> Component.literal(usedThread + " / " + totalThread),
            () -> ECOHostStyles.ratio(usedThread, totalThread)
        ));
        cpuPool.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.computation.cpu_storage",
            () -> Component.literal(Tooltips.ofBytes(getUsedComputationBytes()).getString() + " / " + Tooltips.ofBytes(totalBytes).getString()),
            () -> ECOHostStyles.ratio(getUsedComputationBytes(), totalBytes)
        ));
        details.addChild(cpuPool);

        UIElement root = ECOHostWidgets.hostPanel(
            () -> getItemFromBlockEntity().getDescription(),
            () -> Component.translatable("gui.neoecoae.host.computation.subtitle"),
            () -> Component.translatable("gui.neoecoae.host.status.online"),
            List.of(
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage"),
                    () -> Component.literal(Tooltips.ofBytes(getUsedComputationBytes()).getString() + " / " + Tooltips.ofBytes(totalBytes).getString()),
                    () -> ECOHostStyles.ratio(getUsedComputationBytes(), totalBytes)
                ),
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.computation.thread_usage"),
                    () -> Component.literal(usedThread + " / " + totalThread),
                    () -> ECOHostStyles.ratio(usedThread, totalThread)
                ),
                ECOHostMetric.scalar(
                    () -> Component.translatable("gui.neoecoae.host.computation.parallel_count"),
                    () -> Component.literal(String.valueOf(parallelCount))
                )
            ),
            details,
            () -> Component.translatable("gui.neoecoae.host.computation.footer"),
            buildWindow
        );

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
```

- [ ] **Step 3: Add helper**

Add:

```java
    private long getUsedComputationBytes() {
        return Math.max(totalBytes - availableBytes, 0);
    }
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/blocks/entity/computation/ECOComputationSystemBlockEntity.java
git commit -m "Redesign computation host UI"
```

---

### Task 8: Replace Crafting Host UI

**Files:**
- Modify: `src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java`
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Add imports**

Add:

```java
import cn.dancingsnow.neoecoae.gui.widget.ECOHostMetric;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostSwitchRow;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
```

Remove old text-panel imports after replacement.

- [ ] **Step 2: Replace `createUI`**

Use this implementation:

```java
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement buildWindow = buildPanel(holder);
        UIElement details = ECOHostWidgets.detailArea(false);
        details.addChild(ECOHostWidgets.sectionTitle("gui.neoecoae.host.crafting.runtime"));
        details.addChild(ECOHostWidgets.tileRow(List.of(
            ECOHostWidgets.tile("gui.neoecoae.host.crafting.pattern_buses", () -> Component.literal(String.valueOf(patternBusCount))),
            ECOHostWidgets.tile("gui.neoecoae.host.crafting.parallel_cores", () -> Component.literal(String.valueOf(parallelCount))),
            ECOHostWidgets.tile("gui.neoecoae.host.crafting.worker_cores", () -> Component.literal(String.valueOf(workerCount))),
            ECOHostWidgets.tile("gui.neoecoae.host.crafting.overflow", () -> Component.literal(getOverflowThreads() + " (" + ECOHostStyles.percent(getOverflowThreads(), threadCount) + "%)"))
        )));

        UIElement cooling = ECOHostWidgets.card();
        cooling.addChild(new Label().setText(Component.translatable("gui.neoecoae.host.crafting.overclock_cooling")).textStyle(ECOHostStyles::valueText));
        cooling.addChild(new Label().bindDataSource(SupplierDataSource.of(() -> Component.translatable(
            "gui.neoecoae.host.crafting.overclock_summary",
            overlockTimes,
            getEffectiveOverclockTimes(),
            getDisplayedCoolingMaxOverclock() < 0 ? "-" : String.valueOf(getDisplayedCoolingMaxOverclock())
        ))).textStyle(ECOHostStyles::hintText));
        cooling.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.crafting.coolant",
            () -> Component.literal(Tooltips.ofNumber(coolant).getString() + " / " + Tooltips.ofNumber(MAX_COOLANT).getString()),
            () -> ECOHostStyles.ratio(coolant, MAX_COOLANT)
        ));
        cooling.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.host.crafting.energy",
            () -> Component.literal(Tooltips.ofNumber(getMaxEnergyUsage()).getString() + "AE/t"),
            () -> 0.0f
        ));
        details.addChild(cooling);

        UIElement controls = ECOHostWidgets.card().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(8);
            layout.height(34);
        });
        controls.addChild(new ECOHostSwitchRow(
            Component.translatable("gui.neoecoae.crafting.enable_overlock"),
            Component.translatable("gui.neoecoae.crafting.overclocked.tooltip"),
            () -> overclocked,
            value -> overclocked = value
        ).layout(layout -> layout.width(205)));
        controls.addChild(new ECOHostSwitchRow(
            Component.translatable("gui.neoecoae.crafting.enable_active_cooling"),
            Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip"),
            () -> activeCooling,
            value -> activeCooling = value
        ).layout(layout -> layout.width(205)));
        details.addChild(controls);

        UIElement root = ECOHostWidgets.hostPanel(
            () -> getItemFromBlockEntity().getDescription(),
            () -> Component.translatable("gui.neoecoae.host.crafting.subtitle"),
            () -> Component.translatable("gui.neoecoae.host.status.running"),
            List.of(
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.crafting.working_threads"),
                    () -> Component.literal(runningThreadCount + " / " + getAvailableThreads()),
                    () -> ECOHostStyles.ratio(runningThreadCount, getAvailableThreads())
                ),
                ECOHostMetric.scalar(
                    () -> Component.translatable("gui.neoecoae.host.crafting.total_parallelism"),
                    () -> Component.literal(String.valueOf(threadCount))
                ),
                ECOHostMetric.scalar(
                    () -> Component.translatable("gui.neoecoae.host.crafting.max_energy_usage"),
                    () -> Component.literal(Tooltips.ofNumber(getMaxEnergyUsage()).getString() + "AE/t")
                )
            ),
            details,
            () -> Component.translatable("gui.neoecoae.host.crafting.footer"),
            buildWindow
        );

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
```

- [ ] **Step 3: Fix overflow helper if necessary**

Current `getOverflowThreads()` uses `Math.min(0, threadCount - getAvailableThreads())`, which never returns a positive overflow. Replace it with:

```java
    private int getOverflowThreads() {
        return Math.max(0, threadCount - getAvailableThreads());
    }
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds. If `SupplierDataSource` import was removed by mistake, re-add `com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingSystemBlockEntity.java
git commit -m "Redesign crafting host UI"
```

---

### Task 9: Final Styling Pass

**Files:**
- Modify: `src/main/resources/assets/neoecoae/lss/eco.lss`
- Possibly modify widget layout constants if compile or visual checks show overflow.
- Verify: `./gradlew compileJava`

- [ ] **Step 1: Add final LSS classes**

Extend the LSS section added in Task 2 to include:

```css
.eco-host-header {
  padding-bottom: 4;
}

.eco-host-status {
  background: #143c4620;
}

.eco-host-card {
  padding-all: 6;
}

.eco-host-tile {
  padding-all: 5;
}

.eco-host-footer {
  padding-top: 4;
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew compileJava
```

Expected: compile succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/assets/neoecoae/lss/eco.lss src/main/java/cn/dancingsnow/neoecoae/gui/widget src/main/java/cn/dancingsnow/neoecoae/blocks/entity
git commit -m "Polish ECO host UI styles"
```

If there are no Java changes in this task, only add `eco.lss`.

---

### Task 10: Verification

**Files:**
- No planned source edits.
- Verify: Gradle compile and optional game run.

- [ ] **Step 1: Compile all Java**

Run:

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Validate resources**

Run:

```bash
python -m json.tool src/main/resources/assets/neoecoae/lang/zh_cn.json
python -m json.tool src/main/resources/assets/neoecoae/lang/zh_hk.json
python -m json.tool src/main/resources/assets/neoecoae/lang/zh_tw.json
python -m json.tool src/main/resources/assets/neoecoae/lang/lzh.json
```

Expected: all succeed.

- [ ] **Step 3: Optional client smoke test**

Run if practical:

```bash
./gradlew runClient
```

Manual checks:

- Open storage host UI.
- Confirm only storage channel list scrolls.
- Open computation host UI.
- Confirm CPU Storage is first and no detail scrolling is required.
- Open crafting host UI.
- Confirm controls are visible and switches stay right-aligned.
- Click the multiblock builder button in each host.
- Confirm the existing builder panel opens.

- [ ] **Step 4: Commit verification-only fixes**

If verification required small fixes:

```bash
git add <changed-files>
git commit -m "Fix ECO host UI verification issues"
```

If no fixes were needed, do not create an empty commit.
