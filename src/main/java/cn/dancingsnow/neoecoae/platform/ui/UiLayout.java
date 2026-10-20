package cn.dancingsnow.neoecoae.platform.ui;

import java.util.ArrayList;
import java.util.List;

/** Deterministic pixel layout for the column/row subset used by the port's first JSON screens. */
public final class UiLayout {
    public enum Direction { COLUMN, ROW }
    public enum Align { START, CENTER, END, STRETCH }
    public record Length(float value, boolean percent) {
        public Length {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite UI length");
        }
        public float resolve(float reference) { return percent ? reference * value / 100 : value; }
        public static Length px(float value) { return new Length(value, false); }
    }

    public static final class Style {
        public Length width, height, left, top;
        public Length paddingLeft = Length.px(0), paddingRight = Length.px(0);
        public Length paddingTop = Length.px(0), paddingBottom = Length.px(0);
        public Length marginLeft = Length.px(0), marginRight = Length.px(0);
        public Length marginTop = Length.px(0), marginBottom = Length.px(0);
        public Length gap = Length.px(0);
        public Direction direction = Direction.COLUMN;
        public Align alignItems = Align.STRETCH, justifyContent = Align.START;
        public boolean absolute, display = true;
    }

    public static final class Node {
        public final Style style = new Style();
        public final List<Node> children = new ArrayList<>();
        public final float intrinsicWidth, intrinsicHeight;
        public float x, y, width, height;

        public Node(float intrinsicWidth, float intrinsicHeight) {
            if (!Float.isFinite(intrinsicWidth) || !Float.isFinite(intrinsicHeight)
                || intrinsicWidth < 0 || intrinsicHeight < 0) throw new IllegalArgumentException("Invalid UI size");
            this.intrinsicWidth = intrinsicWidth;
            this.intrinsicHeight = intrinsicHeight;
        }
    }

    private UiLayout() {}

    public static void arrange(Node root) {
        Size natural = measure(root, 0);
        place(root, 0, 0, natural.width, natural.height);
    }

    private record Size(float width, float height) {}

    private static Size measure(Node node, float referenceWidth) {
        if (!node.style.display) return new Size(0, 0);
        Style s = node.style;
        float pl = s.paddingLeft.resolve(referenceWidth), pr = s.paddingRight.resolve(referenceWidth);
        float pt = s.paddingTop.resolve(referenceWidth), pb = s.paddingBottom.resolve(referenceWidth);
        float knownWidth = s.width != null && !s.width.percent ? s.width.value : referenceWidth;
        float innerReference = Math.max(0, knownWidth - pl - pr);
        float main = 0, cross = 0;
        int count = 0;
        for (Node child : node.children) {
            if (child.style.absolute || !child.style.display) continue;
            Size size = measure(child, innerReference);
            Style cs = child.style;
            float w = size.width + cs.marginLeft.resolve(innerReference) + cs.marginRight.resolve(innerReference);
            float h = size.height + cs.marginTop.resolve(innerReference) + cs.marginBottom.resolve(innerReference);
            main += s.direction == Direction.COLUMN ? h : w;
            cross = Math.max(cross, s.direction == Direction.COLUMN ? w : h);
            count++;
        }
        main += Math.max(0, count - 1) * s.gap.resolve(innerReference);
        float w = Math.max(node.intrinsicWidth, s.direction == Direction.COLUMN ? cross : main) + pl + pr;
        float h = Math.max(node.intrinsicHeight, s.direction == Direction.COLUMN ? main : cross) + pt + pb;
        if (s.width != null && (!s.width.percent || referenceWidth > 0)) w = s.width.resolve(referenceWidth);
        if (s.height != null && !s.height.percent) h = s.height.value;
        return new Size(Math.max(0, w), Math.max(0, h));
    }

    private static void place(Node node, float x, float y, float width, float height) {
        node.x = x; node.y = y;
        node.width = node.style.display ? Math.max(0, width) : 0;
        node.height = node.style.display ? Math.max(0, height) : 0;
        Style s = node.style;
        float pl = s.paddingLeft.resolve(node.width), pr = s.paddingRight.resolve(node.width);
        float pt = s.paddingTop.resolve(node.width), pb = s.paddingBottom.resolve(node.width);
        float innerW = Math.max(0, node.width - pl - pr), innerH = Math.max(0, node.height - pt - pb);
        float totalMain = 0;
        int count = 0;
        for (Node child : node.children) {
            if (!child.style.display || child.style.absolute) continue;
            Size size = measure(child, innerW);
            totalMain += s.direction == Direction.COLUMN
                ? resolved(child.style.height, size.height, innerH)
                    + child.style.marginTop.resolve(innerW) + child.style.marginBottom.resolve(innerW)
                : resolved(child.style.width, size.width, innerW)
                    + child.style.marginLeft.resolve(innerW) + child.style.marginRight.resolve(innerW);
            count++;
        }
        totalMain += Math.max(0, count - 1) * s.gap.resolve(innerW);
        float main = offset(s.justifyContent,
            (s.direction == Direction.COLUMN ? innerH : innerW) - totalMain);
        for (Node child : node.children) {
            Style cs = child.style;
            if (!cs.display) { place(child, 0, 0, 0, 0); continue; }
            Size natural = measure(child, innerW);
            float ml = cs.marginLeft.resolve(innerW), mr = cs.marginRight.resolve(innerW);
            float mt = cs.marginTop.resolve(innerW), mb = cs.marginBottom.resolve(innerW);
            float w = resolved(cs.width, natural.width, innerW), h = resolved(cs.height, natural.height, innerH);
            if (cs.absolute) {
                place(child, pl + resolved(cs.left, 0, innerW), pt + resolved(cs.top, 0, innerH), w, h);
            } else if (s.direction == Direction.COLUMN) {
                if (s.alignItems == Align.STRETCH && cs.width == null) w = Math.max(0, innerW - ml - mr);
                place(child, pl + ml + offset(s.alignItems, innerW - ml - mr - w), pt + main + mt, w, h);
                main += mt + h + mb + s.gap.resolve(innerW);
            } else {
                if (s.alignItems == Align.STRETCH && cs.height == null) h = Math.max(0, innerH - mt - mb);
                place(child, pl + main + ml, pt + mt + offset(s.alignItems, innerH - mt - mb - h), w, h);
                main += ml + w + mr + s.gap.resolve(innerW);
            }
        }
    }

    private static float resolved(Length length, float fallback, float reference) {
        return length == null ? fallback : length.resolve(reference);
    }

    private static float offset(Align align, float free) {
        return switch (align) { case CENTER -> free / 2; case END -> free; default -> 0; };
    }
}
