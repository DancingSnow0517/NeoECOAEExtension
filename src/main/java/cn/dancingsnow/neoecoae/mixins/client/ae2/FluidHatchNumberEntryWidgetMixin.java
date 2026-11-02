package cn.dancingsnow.neoecoae.mixins.client.ae2;

import appeng.client.gui.NumberEntryType;
import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.ValidationIcon;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchLongAmountWidget;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchStockAmounts;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original widget remains in use; every hook is gated on this one widget's hatch flag. */
@Mixin(NumberEntryWidget.class)
public abstract class FluidHatchNumberEntryWidgetMixin implements FluidHatchLongAmountWidget {
    @Shadow @Final private ConfirmableTextField textField;
    @Shadow @Final private DecimalFormat decimalFormat;
    @Shadow @Final private int errorTextColor;
    @Shadow @Final private int normalTextColor;
    @Shadow private NumberEntryType type;
    @Shadow private long minValue;
    @Shadow private long maxValue;
    @Shadow private ValidationIcon validationIcon;
    @Shadow protected abstract Optional<BigDecimal> getValueInternal();
    @Shadow protected abstract boolean isNumber();
    @Shadow protected abstract void setValueInternal(BigDecimal value);
    @Unique private boolean neoecoae$longFluidAmount;

    @Override public void neoecoae$enableLongFluidAmount() {
        neoecoae$longFluidAmount = true;
        textField.setMaxLength(64);
    }

    @Inject(method = "getLongValue", at = @At("HEAD"), cancellable = true)
    private void neoecoae$checkedLongValue(CallbackInfoReturnable<OptionalLong> cir) {
        if (neoecoae$longFluidAmount) {
            cir.setReturnValue(getValueInternal().map(value ->
                FluidHatchStockAmounts.checkedValue(value, type.amountPerUnit(), minValue, maxValue))
                .orElseGet(OptionalLong::empty));
        }
    }

    @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
    private void neoecoae$validateExactRange(CallbackInfo ci) {
        if (!neoecoae$longFluidAmount) return;
        List<Component> errors = new ArrayList<>();
        List<Component> information = new ArrayList<>();
        var possible = getValueInternal();
        if (possible.isEmpty()) {
            errors.add(GuiText.InvalidNumber.text());
        } else if (type.amountPerUnit() == 1 && possible.get().scale() > 0) {
            errors.add(GuiText.NumberNonInteger.text());
        } else {
            var external = FluidHatchStockAmounts.externalValue(possible.get(), type.amountPerUnit());
            var divisor = BigDecimal.valueOf(type.amountPerUnit());
            if (possible.get().signum() < 0 || external.compareTo(BigDecimal.valueOf(minValue)) < 0) {
                errors.add(GuiText.NumberLessThanMinValue.text(decimalFormat.format(
                    BigDecimal.valueOf(minValue).divide(divisor, MathContext.DECIMAL128))));
            } else if (external.compareTo(BigDecimal.valueOf(maxValue)) > 0) {
                errors.add(GuiText.NumberGreaterThanMaxValue.text(decimalFormat.format(
                    BigDecimal.valueOf(maxValue).divide(divisor, MathContext.DECIMAL128))));
            } else if (!isNumber()) {
                information.add(Component.literal("= " + decimalFormat.format(possible.get())));
            }
        }
        boolean valid = errors.isEmpty();
        var tooltip = valid ? information : errors;
        textField.setTextColor(valid ? normalTextColor : errorTextColor);
        textField.setTooltipMessage(tooltip);
        if (validationIcon != null) {
            validationIcon.setValid(valid);
            validationIcon.setTooltip(tooltip);
        }
        ci.cancel();
    }

    @Inject(method = "addQty", at = @At("HEAD"), cancellable = true)
    private void neoecoae$clampWithoutLosingMillibuckets(long delta, CallbackInfo ci) {
        if (!neoecoae$longFluidAmount) return;
        var current = getValueInternal().orElse(BigDecimal.ZERO);
        var next = current.add(BigDecimal.valueOf(delta));
        var unit = BigDecimal.valueOf(type.amountPerUnit());
        var minimum = BigDecimal.valueOf(minValue).divide(unit, MathContext.DECIMAL128);
        var maximum = BigDecimal.valueOf(maxValue).divide(unit, MathContext.DECIMAL128);
        if (next.compareTo(minimum) < 0) next = minimum;
        else if (next.compareTo(maximum) > 0) next = maximum;
        else if (current.compareTo(BigDecimal.ONE) == 0 && delta > 1) next = next.subtract(BigDecimal.ONE);
        setValueInternal(next);
        ci.cancel();
    }
}
