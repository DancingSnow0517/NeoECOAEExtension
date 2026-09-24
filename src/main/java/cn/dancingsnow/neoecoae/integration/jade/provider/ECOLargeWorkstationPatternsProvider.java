package cn.dancingsnow.neoecoae.integration.jade.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

/** Shows the encoded recipes stored in the large workstation's AE2 pattern provider. */
public enum ECOLargeWorkstationPatternsProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_PATTERN_COUNT = "patternCount";
    private static final String TAG_PATTERN_CAPACITY = "patternCapacity";
    private static final String TAG_PATTERNS = "workstationPatterns";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_OUTPUTS = "outputs";
    private static final String TAG_INPUTS = "inputs";
    private static final String TAG_ALTERNATIVES = "alternatives";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ECOLargeIntegratedWorkingStationInterfaceBlockEntity host)
                || host.getLevel() == null) {
            return;
        }

        var inventory = host.getWorkstationProvider().getPatternInv();
        HolderLookup.Provider registries = host.getLevel().registryAccess();
        ListTag patterns = new ListTag();
        int stored = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            stored++;

            CompoundTag patternTag = new CompoundTag();
            patternTag.putInt(TAG_SLOT, slot + 1);
            IPatternDetails details = null;
            try {
                details = PatternDetailsHelper.decodePattern(stack, host.getLevel());
            } catch (RuntimeException ignored) {
                // Keep the slot visible even when another mod's pattern decoder is unavailable.
            }
            if (details != null) {
                try {
                    writeRecipe(registries, details, patternTag);
                } catch (RuntimeException ignored) {
                    patternTag.remove(TAG_OUTPUTS);
                    patternTag.remove(TAG_INPUTS);
                }
            }
            patterns.add(patternTag);
        }

        data.putInt(TAG_PATTERN_COUNT, stored);
        data.putInt(TAG_PATTERN_CAPACITY, inventory.size());
        data.put(TAG_PATTERNS, patterns);
    }

    private static void writeRecipe(HolderLookup.Provider registries, IPatternDetails details, CompoundTag patternTag) {
        ListTag outputs = new ListTag();
        List<GenericStack> recipeOutputs = details.getOutputs();
        if (recipeOutputs != null) {
            for (GenericStack output : recipeOutputs) {
                if (output != null && output.amount() > 0) {
                    outputs.add(GenericStack.writeTag(registries, output));
                }
            }
        }
        patternTag.put(TAG_OUTPUTS, outputs);

        ListTag inputs = new ListTag();
        IPatternDetails.IInput[] recipeInputs = details.getInputs();
        if (recipeInputs != null) {
            for (IPatternDetails.IInput input : recipeInputs) {
                if (input == null) continue;
                GenericStack[] candidates = input.getPossibleInputs();
                if (candidates == null || candidates.length == 0) continue;

                long multiplier = Math.max(1L, input.getMultiplier());
                ListTag alternatives = new ListTag();
                for (GenericStack candidate : candidates) {
                    if (candidate == null || candidate.amount() <= 0) continue;
                    long amount = candidate.amount() > Long.MAX_VALUE / multiplier
                            ? Long.MAX_VALUE
                            : candidate.amount() * multiplier;
                    alternatives.add(GenericStack.writeTag(registries,
                            new GenericStack(candidate.what(), amount)));
                }
                if (!alternatives.isEmpty()) {
                    CompoundTag inputTag = new CompoundTag();
                    inputTag.put(TAG_ALTERNATIVES, alternatives);
                    inputs.add(inputTag);
                }
            }
        }
        patternTag.put(TAG_INPUTS, inputs);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(TAG_PATTERN_COUNT)) return;

        int count = data.getInt(TAG_PATTERN_COUNT);
        int capacity = data.getInt(TAG_PATTERN_CAPACITY);
        tooltip.add(Component.translatable("jade.neoecoae.large_workstation.patterns", count, capacity)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (count == 0) {
            tooltip.add(Component.translatable("jade.neoecoae.large_workstation.patterns.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        ListTag patterns = data.getList(TAG_PATTERNS, Tag.TAG_COMPOUND);
        for (int index = 0; index < patterns.size(); index++) {
            CompoundTag pattern = patterns.getCompound(index);
            tooltip.add(Component.translatable("jade.neoecoae.large_workstation.pattern",
                            pattern.getInt(TAG_SLOT))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            appendStacks(tooltip, accessor, pattern.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND),
                    "jade.neoecoae.large_workstation.outputs", ChatFormatting.GREEN);

            ListTag inputs = pattern.getList(TAG_INPUTS, Tag.TAG_COMPOUND);
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                appendInput(tooltip, accessor, inputs.getCompound(inputIndex), inputIndex + 1);
            }
            if (pattern.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND).isEmpty()) {
                tooltip.add(Component.translatable("jade.neoecoae.large_workstation.pattern.unreadable")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void appendStacks(ITooltip tooltip, BlockAccessor accessor, ListTag stacks,
                                     String labelKey, ChatFormatting valueColor) {
        if (stacks.isEmpty()) return;
        MutableComponent line = Component.translatable(labelKey).withStyle(ChatFormatting.GRAY);
        boolean hasValue = false;
        for (int index = 0; index < stacks.size(); index++) {
            GenericStack stack = GenericStack.readTag(accessor.getLevel().registryAccess(), stacks.getCompound(index));
            if (stack == null) continue;
            if (hasValue) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            appendStack(line, stack, valueColor);
            hasValue = true;
        }
        if (hasValue) tooltip.add(line);
    }

    private static void appendInput(ITooltip tooltip, BlockAccessor accessor, CompoundTag inputTag, int inputNumber) {
        ListTag alternatives = inputTag.getList(TAG_ALTERNATIVES, Tag.TAG_COMPOUND);
        MutableComponent line = Component.translatable("jade.neoecoae.large_workstation.input", inputNumber)
                .withStyle(ChatFormatting.GRAY);
        boolean hasValue = false;
        for (int index = 0; index < alternatives.size(); index++) {
            GenericStack stack = GenericStack.readTag(accessor.getLevel().registryAccess(),
                    alternatives.getCompound(index));
            if (stack == null) continue;
            if (hasValue) {
                line.append(Component.translatable("jade.neoecoae.large_workstation.or")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            appendStack(line, stack, ChatFormatting.YELLOW);
            hasValue = true;
        }
        if (hasValue) tooltip.add(line);
    }

    private static void appendStack(MutableComponent line, GenericStack stack, ChatFormatting amountColor) {
        line.append(stack.what().getDisplayName().copy().withStyle(ChatFormatting.WHITE));
        line.append(Component.literal(" × " + stack.what().formatAmount(stack.amount(), AmountFormat.SLOT))
                .withStyle(amountColor));
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL;
    }

    @Override
    public ResourceLocation getUid() {
        return NeoECOAE.id("large_workstation_patterns");
    }
}
