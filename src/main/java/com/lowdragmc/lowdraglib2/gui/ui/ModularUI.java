package com.lowdragmc.lowdraglib2.gui.ui;

import net.minecraft.world.entity.player.Player;

public class ModularUI {
    private final Object root;
    private final Player player;

    public ModularUI(Object root, Player player) {
        this.root = root;
        this.player = player;
    }

    public Object root() {
        return root;
    }

    public Player player() {
        return player;
    }
}
