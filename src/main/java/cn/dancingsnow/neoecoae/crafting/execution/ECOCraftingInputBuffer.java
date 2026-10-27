package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.List;

final class ECOCraftingInputBuffer {
    private final List<KeyCounter> slots = new ArrayList<>();
    private final KeyCounter outputs = new KeyCounter();
    private final KeyCounter remainders = new KeyCounter();
    private KeyCounter[] inputs = new KeyCounter[0];

    KeyCounter[] prepare(int inputCount) {
        clear();
        while (slots.size() < inputCount) slots.add(new KeyCounter());
        if (inputs.length != inputCount) {
            inputs = new KeyCounter[inputCount];
            for (int slot = 0; slot < inputCount; slot++) inputs[slot] = slots.get(slot);
        }
        return inputs;
    }

    KeyCounter outputs() {
        return outputs;
    }

    KeyCounter remainders() {
        return remainders;
    }

    void clear() {
        clear(outputs);
        clear(remainders);
        for (var input : inputs) clear(input);
    }

    private static void clear(KeyCounter counter) {
        counter.clear();
        counter.removeEmptySubmaps();
    }
}
