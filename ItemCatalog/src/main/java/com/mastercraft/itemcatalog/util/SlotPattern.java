package com.mastercraft.itemcatalog.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the slot-pattern strings used by gui.category-slots and
 * gui.item-slots in config.yml, e.g. {@code "10-16,19-25,28-34"}:
 * a comma-separated list of single 0-based slot indices and/or inclusive
 * ranges ("a-b"). Invalid tokens are ignored; out-of-range indices are
 * dropped rather than throwing, so a typo in config never crashes the GUI.
 */
public final class SlotPattern {

    private SlotPattern() {}

    /** @param maxExclusive the inventory size (rows * 9) — any parsed slot outside [0, maxExclusive) is dropped. */
    public static List<Integer> parse(String pattern, int maxExclusive) {
        return parse(pattern, maxExclusive, false);
    }

    /**
     * @param maxExclusive the inventory size (rows * 9) — any parsed slot outside [0, maxExclusive) is dropped.
     * @param verticalFill if true, the returned order is re-sorted to fill column-by-column
     *                     (top-to-bottom, then next column to the right) instead of row-by-row —
     *                     e.g. slots 10,19,28,37 (column) then 11,20,29,38 (next column), which is
     *                     the order MMOItems' own item browser fills its grid in. The set of slots
     *                     is unaffected either way, only the order they're handed out in.
     */
    public static List<Integer> parse(String pattern, int maxExclusive, boolean verticalFill) {
        List<Integer> ordered = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) return ordered;

        for (String rawPart : pattern.split(",")) {
            String part = rawPart.trim();
            if (part.isEmpty()) continue;

            if (part.contains("-")) {
                String[] bounds = part.split("-", 2);
                try {
                    int start = Integer.parseInt(bounds[0].trim());
                    int end = Integer.parseInt(bounds[1].trim());
                    if (start > end) {
                        int tmp = start;
                        start = end;
                        end = tmp;
                    }
                    for (int i = start; i <= end; i++) {
                        if (i >= 0 && i < maxExclusive) ordered.add(i);
                    }
                } catch (NumberFormatException ignored) {
                    DebugLogger.warn("Invalid slot range '" + part + "' in a GUI slot pattern, ignoring it.");
                }
            } else {
                try {
                    int value = Integer.parseInt(part);
                    if (value >= 0 && value < maxExclusive) ordered.add(value);
                } catch (NumberFormatException ignored) {
                    DebugLogger.warn("Invalid slot '" + part + "' in a GUI slot pattern, ignoring it.");
                }
            }
        }

        List<Integer> deduped = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int v : ordered) {
            if (seen.add(v)) deduped.add(v);
        }

        if (verticalFill) {
            deduped.sort(Comparator.comparingInt((Integer s) -> s % 9).thenComparingInt(s -> s / 9));
        }

        return deduped;
    }
}
