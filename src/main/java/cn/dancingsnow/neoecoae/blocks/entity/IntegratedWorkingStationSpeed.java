package cn.dancingsnow.neoecoae.blocks.entity;

final class IntegratedWorkingStationSpeed {
    static final int MAX_PROGRESS_PER_TICK = 200;

    private IntegratedWorkingStationSpeed() {}

    static int calculate(int ae2SpeedCards, Iterable<Integer> addonMultipliers) {
        int speed = ae2BaseSpeed(ae2SpeedCards);
        for (int multiplier : addonMultipliers) {
            speed = saturatedMultiply(speed, Math.max(1, multiplier));
        }
        return Math.max(1, Math.min(MAX_PROGRESS_PER_TICK, speed));
    }

    static int ae2BaseSpeed(int speedCards) {
        return switch (speedCards) {
            case 0 -> 2;
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 50;
            default -> speedCards < 0 ? 2 : 50;
        };
    }

    static int energyForProgress(int totalEnergy, int currentProgress, int progressAdvance) {
        if (totalEnergy <= 0 || progressAdvance <= 0) {
            return 0;
        }
        int start = Math.max(0, Math.min(MAX_PROGRESS_PER_TICK, currentProgress));
        int end = Math.max(start, Math.min(MAX_PROGRESS_PER_TICK, start + progressAdvance));
        long energyAtStart = (long) totalEnergy * start / MAX_PROGRESS_PER_TICK;
        long energyAtEnd = (long) totalEnergy * end / MAX_PROGRESS_PER_TICK;
        return (int) Math.min(Integer.MAX_VALUE, energyAtEnd - energyAtStart);
    }

    private static int saturatedMultiply(int left, int right) {
        if (left >= MAX_PROGRESS_PER_TICK || right > MAX_PROGRESS_PER_TICK / left) {
            return MAX_PROGRESS_PER_TICK;
        }
        return left * right;
    }
}
