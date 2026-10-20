package cn.dancingsnow.neoecoae.all;

import com.google.common.base.Suppliers;
import lombok.Getter;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

@Getter
public enum NEToolTier implements Tier {
    ALUMINUM(BlockTags.INCORRECT_FOR_IRON_TOOL, 250, 6.0F, 2.0F, 14, () -> Ingredient.of(NETags.Items.ALUMINUM_INGOT)),
    ALUMINUM_ALLOY(BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 6.0F, 2.0F, 14, () -> Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT)),
    TUNGSTEN(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1700, 8.0F, 3.0F, 10, () -> Ingredient.of(NETags.Items.TUNGSTEN_INGOT)),
    BLACK_TUNGSTEN_ALLOY(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2500, 9.9f, 4.0f, 15, () -> Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT));

    private final TagKey<Block> incorrectBlocksForDrops;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final Supplier<Ingredient> ingredientSupplier;

    NEToolTier(TagKey<Block> incorrectBlockForDrops, int uses, float speed, float attackDamageBonus, int enchantmentValue, Supplier<Ingredient> ingredientSupplier) {
        this.incorrectBlocksForDrops = incorrectBlockForDrops;
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.ingredientSupplier = Suppliers.memoize(ingredientSupplier::get);
    }


    @Override
    public Ingredient getRepairIngredient() {
        return ingredientSupplier.get();
    }
}
