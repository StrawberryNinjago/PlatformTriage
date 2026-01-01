package com.example.Triage.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

@UtilityClass
public class IndexMatchers {

    public static boolean sameSet(List<String> a, List<String> b) {
        if (a == null || b == null)
            return false;
        return normalizeSet(a).equals(normalizeSet(b));
    }

    public static boolean containsAllIgnoreOrder(List<String> indexCols, List<String> wantedCols) {
        if (indexCols == null || wantedCols == null)
            return false;
        Set<String> idx = normalizeSet(indexCols);
        for (String w : normalizeList(wantedCols)) {
            if (!idx.contains(w))
                return false;
        }
        return true;
    }

    /**
     * True if index columns start with the wanted columns in the same order (common
     * for composite indexes).
     */
    public static boolean startsWithPrefix(List<String> indexCols, List<String> wantedPrefix) {
        if (indexCols == null || wantedPrefix == null)
            return false;

        List<String> idx = normalizeList(indexCols);
        List<String> want = normalizeList(wantedPrefix);

        if (want.isEmpty() || idx.size() < want.size())
            return false;

        for (int i = 0; i < want.size(); i++) {
            if (!Objects.equals(idx.get(i), want.get(i)))
                return false;
        }
        return true;
    }

    private static Set<String> normalizeSet(List<String> cols) {
        return normalizeList(cols).stream().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> normalizeList(List<String> cols) {
        if (cols == null)
            return List.of();
        return cols.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }
}
