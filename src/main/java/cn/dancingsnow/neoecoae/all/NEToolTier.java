package cn.dancingsnow.neoecoae.all;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import lombok.Getter;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

@Getter
public enum NEToolTier implements Tier {
    ALUMINUM(2, 250, 6.0F, 2.0F, 14, () -> Ingredient.of(NETags.Items.ALUMINUM_INGOT)),
    ALUMINUM_ALLOY(2, 500, 6.0F, 2.0F, 14, () -> Ingredient.of(NETags.Items.ALUMINUM_ALLOY_INGOT)),
    TUNGSTEN(3, 1700, 8.0F, 3.0F, 10, () -> Ingredient.of(NETags.Items.TUNGSTEN_INGOT)),
    BLACK_TUNGSTEN_ALLOY(4, 2500, 9.9f, 4.0f, 15, () -> Ingredient.of(NETags.Items.BLACK_TUNGSTEN_ALLOY_INGOT));

    private final int level;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final Supplier<Ingredient> ingredientSupplier;

    NEToolTier(
            int level,
            int uses,
            float speed,
            float attackDamageBonus,
            int enchantmentValue,
            Supplier<Ingredient> ingredientSupplier) {
        this.level = level;
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
