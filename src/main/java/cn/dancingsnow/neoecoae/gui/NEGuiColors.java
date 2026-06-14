package cn.dancingsnow.neoecoae.gui;

public final class NEGuiColors {
    private NEGuiColors() {
    }

    public static int textColor(int color) {
        if ((color & 0xff000000) == 0) {
            return color | 0xff000000;
        }
        return color;
    }
}
