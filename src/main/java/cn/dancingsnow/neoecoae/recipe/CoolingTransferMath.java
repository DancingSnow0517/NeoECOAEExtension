package cn.dancingsnow.neoecoae.recipe;

public final class CoolingTransferMath {
    private CoolingTransferMath() {}

    public static long inputForDeficit(int deficit, int recipeInput, int recipeCoolant) {
        if (deficit <= 0) {
            return 0;
        }
        if (recipeInput <= 0 || recipeCoolant <= 0) {
            throw new IllegalArgumentException("Cooling recipe input and coolant must be positive");
        }
        long numerator = (long) deficit * recipeInput;
        return numerator / recipeCoolant + (numerator % recipeCoolant == 0 ? 0 : 1);
    }

    public static int scaleAmount(int consumedInput, int recipeAmount, int recipeInput) {
        if (consumedInput < 0 || recipeAmount < 0 || recipeInput <= 0) {
            throw new IllegalArgumentException("Cooling transfer amounts must be non-negative and input positive");
        }
        long scaled = (long) consumedInput * recipeAmount / recipeInput;
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }
}
