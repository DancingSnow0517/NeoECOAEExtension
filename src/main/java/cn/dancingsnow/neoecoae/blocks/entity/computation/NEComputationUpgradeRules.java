package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.all.NETags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Rules for the optional GregTech upgrade slot on the computation controller. */
public final class NEComputationUpgradeRules {
    public static final int FIELD_GENERATOR_COUNT = 16;
    public static final int INFINITE_COMPONENT_COUNT = 64;
    /**
     * ECOCraftingCPULogic adds one to the co-processor count. Keep that addition
     * from overflowing while still allowing the largest useful int value.
     */
    public static final int MAX_SAFE_ACCELERATORS = Integer.MAX_VALUE - 1;

    private NEComputationUpgradeRules() {}

    public static boolean isGregTechAvailable() {
        try {
            return ModList.get().isLoaded("gtceu")
                    || ModList.get().isLoaded("gtm")
                    || ModList.get().isLoaded("gregtech");
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isValid(ItemStack stack) {
        if (!isGregTechAvailable() || stack.isEmpty()) {
            return false;
        }
        if (isInfiniteComponent(stack)) {
            return stack.getCount() == INFINITE_COMPONENT_COUNT;
        }
        return isFieldGenerator(stack) && stack.getCount() >= FIELD_GENERATOR_COUNT;
    }

    public static int requiredCount(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (isInfiniteComponent(stack)) {
            return INFINITE_COMPONENT_COUNT;
        }
        if (isFieldGenerator(stack)) {
            return FIELD_GENERATOR_COUNT;
        }
        return 0;
    }

    public static int fieldGeneratorMultiplier(ItemStack stack) {
        if (!isGregTechAvailable() || stack.isEmpty() || stack.getCount() != FIELD_GENERATOR_COUNT) {
            return 1;
        }
        return fieldGeneratorMultiplier(ForgeRegistries.ITEMS.getKey(stack.getItem()), stack.getCount());
    }

    private static boolean isFieldGenerator(ItemStack stack) {
        if (!isGregTechAvailable() || stack.isEmpty()) {
            return false;
        }
        return fieldGeneratorMultiplier(ForgeRegistries.ITEMS.getKey(stack.getItem())) > 1;
    }

    static int fieldGeneratorMultiplier(ResourceLocation itemId, int count) {
        if (count != FIELD_GENERATOR_COUNT) {
            return 1;
        }
        return fieldGeneratorMultiplier(itemId);
    }

    private static int fieldGeneratorMultiplier(ResourceLocation itemId) {
        if (itemId == null || !isGregTechNamespace(itemId.getNamespace())) {
            return 1;
        }
        return switch (itemId.getPath()) {
            case "iv_field_generator" -> 2;
            case "luv_field_generator" -> 4;
            case "zpm_field_generator" -> 8;
            case "uv_field_generator" -> 16;
            default -> 1;
        };
    }

    public static boolean hasInfiniteCapacity(ItemStack stack) {
        return isGregTechAvailable() && stack.getCount() == INFINITE_COMPONENT_COUNT && isInfiniteComponent(stack);
    }

    private static boolean isInfiniteComponent(ItemStack stack) {
        return stack.is(NETags.Items.INFINITE_CELL_COMPONENTS);
    }

    private static boolean isGregTechNamespace(String namespace) {
        return "gtceu".equals(namespace) || "gtm".equals(namespace) || "gregtech".equals(namespace);
    }
}
