package com.wahshi.cryptoexplosionradar;

import java.util.*;

/** Cursor is the last attempted symbol, not a position in a changing universe. */
public final class FairRotation {
    private FairRotation() {}
    public static List<String> next(Collection<String> symbols, String after, int limit) {
        List<String> sorted = new ArrayList<>(new TreeSet<>(symbols));
        List<String> out = new ArrayList<>();
        if (sorted.isEmpty() || limit <= 0) return out;
        int start = 0;
        while (start < sorted.size() && sorted.get(start).compareTo(after) <= 0) start++;
        for (int i=0; i<Math.min(limit, sorted.size()); i++)
            out.add(sorted.get((start+i)%sorted.size()));
        return out;
    }
}
