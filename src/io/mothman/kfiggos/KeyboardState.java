// Made by Mothman
package io.mothman.kfiggos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class KeyboardState {
    final String enabled;
    final String selected;
    final int subtype;

    KeyboardState(String enabled, String selected, int subtype) {
        this.enabled = enabled;
        this.selected = selected;
        this.subtype = subtype;
    }

    static String id(String entry) {
        return entry.split(";", 2)[0];
    }

    boolean contains(String inputMethod) {
        for (String entry : enabled.split(":")) {
            if (id(entry).equals(inputMethod)) return true;
        }
        return false;
    }

    boolean valid() {
        return !enabled.isEmpty() && !selected.isEmpty() && contains(selected);
    }

    KeyboardState available(Set<String> installed) {
        List<String> entries = new ArrayList<>();
        for (String entry : enabled.split(":")) {
            if (installed.contains(id(entry))) entries.add(entry);
        }
        if (entries.isEmpty()) return null;
        String filtered = String.join(":", entries);
        boolean keepSelection = entries.stream().anyMatch(e -> id(e).equals(selected));
        return new KeyboardState(filtered, keepSelection ? selected : id(entries.get(0)),
                keepSelection ? subtype : -1);
    }

    boolean sameAs(KeyboardState other) {
        return other != null && selected.equals(other.selected) && subtype == other.subtype
                && entries(enabled).equals(entries(other.enabled));
    }

    boolean addsSystemKeyboard(KeyboardState previous, Set<String> systemKeyboards) {
        if (previous == null) return false;
        for (String entry : enabled.split(":")) {
            String keyboard = id(entry);
            if (systemKeyboards.contains(keyboard) && !previous.contains(keyboard)) return true;
        }
        return false;
    }

    private static Set<String> entries(String value) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : value.split(":")) {
            String[] parts = entry.split(";");
            Set<String> subtypes = new java.util.TreeSet<>();
            for (int i = 1; i < parts.length; i++) subtypes.add(parts[i]);
            normalized.add(parts[0] + ";" + String.join(";", subtypes));
        }
        return normalized;
    }

    String unionWith(KeyboardState current) {
        List<String> entries = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (String entry : enabled.split(":")) {
            entries.add(entry);
            ids.add(id(entry));
        }
        for (String entry : current.enabled.split(":")) {
            if (!entry.isEmpty() && ids.add(id(entry))) entries.add(entry);
        }
        return String.join(":", entries);
    }
}
