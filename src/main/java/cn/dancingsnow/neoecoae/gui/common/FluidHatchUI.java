package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.util.TextFormattingUtil;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class FluidHatchUI {
    private static final ResourceLocation JSON_LAYOUT =
        NeoECOAE.id("screens/fluid_hatch");

    private FluidHatchUI() {
    }

    public static ModularUI createInputHatchUI(BlockUIMenuType.BlockUIHolder holder,
        cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity host) {
        return FluidInputHatchUI.create(host, holder.player);
    }

    public static ModularUI createFluidHatchUI(BlockUIMenuType.BlockUIHolder holder, FluidTank tank, String titleKey,
        boolean allowClickFilled, boolean allowClickDrained) {
        return ECOModularUI.create(createRoot(tank, titleKey, allowClickFilled, allowClickDrained), holder.player);
    }

    static UIElement createRoot(FluidTank tank, String titleKey, boolean allowClickFilled, boolean allowClickDrained) {
        return NeoUiJsonLoader.load(JSON_LAYOUT, new JsonContext(tank, titleKey, allowClickFilled, allowClickDrained));
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final FluidTank tank;
        private final String titleKey;
        private final boolean allowClickFilled;
        private final boolean allowClickDrained;

        private JsonContext(FluidTank tank, String titleKey, boolean allowClickFilled, boolean allowClickDrained) {
            this.tank = tank;
            this.titleKey = titleKey;
            this.allowClickFilled = allowClickFilled;
            this.allowClickDrained = allowClickDrained;
        }

        @Override
        public UIElement createCustom(JsonObject definition) {
            return switch (definition.get("custom").getAsString()) {
                case "title" -> {
                    UIElement titleContainer = new UIElement();
                    titleContainer.addChild(new TextElement()
                        .setText(titleKey, true)
                        .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true)));
                    yield titleContainer;
                }
                case "fluid_slot" -> {
                    UIElement slotContainer = new UIElement();
                    slotContainer.addChild((tank instanceof LongFluidTank longTank ? new LongBufferSlot(longTank) : new FluidSlot())
                        .bind(tank, 0)
                        .setAllowClickDrained(allowClickDrained)
                        .setAllowClickFilled(allowClickFilled)
                        .slotStyle(slotStyle -> slotStyle.fillDirection(FillDirection.DOWN_TO_UP))
                        .addClass("panel_border"));
                    yield slotContainer;
                }
                default -> throw new IllegalArgumentException("Unknown fluid hatch custom element: "
                    + definition.get("custom").getAsString());
            };
        }
    }

    /** Keep the original tank UI while synchronizing preserved stock beyond the fluid API's int view. */
    private static final class LongBufferSlot extends FluidSlot {
        private final BindableValue<Long> exactAmount;
        private final LongFluidTank tank;

        LongBufferSlot(LongFluidTank tank) {
            this.tank = tank;
            exactAmount = new BindableValue<>(tank.getStoredAmount());
            exactAmount.setDisplay(false);
            exactAmount.bind(DataBindingBuilder.longValS2C(tank::getStoredAmount).build());
            addChild(exactAmount);
        }

        @Override public Component getFluidAmountText() {
            if (exactAmount == null || getValue().isEmpty()) return Component.empty();
            return Component.literal(TextFormattingUtil.formatLongToCompactStringBuckets(exactAmount.getValue(), 3) + "B");
        }

        @Override public List<Component> getFullTooltipTexts(boolean withFluidName) {
            var lines = super.getFullTooltipTexts(withFluidName);
            if (exactAmount != null && exactAmount.getValue() > Integer.MAX_VALUE) {
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).getContents() instanceof TranslatableContents text && text.getKey().equals("ldlib.fluid.amount")) {
                        lines.set(i, Component.translatable("ldlib.fluid.amount", exactAmount.getValue(), tank.getCapacity()).append(" mB"));
                    }
                }
            }
            return lines;
        }
    }
}
