package dc.dcessentials.modules.items.tooltip;

public enum TooltipMode {
    COMPACT,
    EXPANDED;

    public static TooltipMode fromString(String raw) {
        if (raw == null) return COMPACT;
        return "EXPANDED".equalsIgnoreCase(raw.trim()) ? EXPANDED : COMPACT;
    }

    public TooltipMode toggle() {
        return this == COMPACT ? EXPANDED : COMPACT;
    }
}
