// Made by Mothman
package io.mothman.kfiggos;

import java.util.Set;

public final class KeyboardStateTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        KeyboardState gboard = new KeyboardState("gboard/.Ime;11;22", "gboard/.Ime", 11);
        KeyboardState reset = new KeyboardState("stock/.Ime:gboard/.Ime;11;22", "stock/.Ime", -1);
        check(gboard.valid(), "A configured keyboard must be valid");
        check(!new KeyboardState("", "", -1).valid(), "Never save an empty list");
        check(!new KeyboardState("stock/.Ime", "missing/.Ime", -1).valid(), "Reject transitional selection");
        check(!gboard.sameAs(reset), "Detect system keyboard added after switching profiles");
        check(gboard.sameAs(new KeyboardState("gboard/.Ime;22;11", "gboard/.Ime", 11)),
                "Subtype enumeration order must not cause unnecessary writes");
        check(gboard.available(Set.of("stock/.Ime")) == null,
                "Keep the system fallback when every saved keyboard has disappeared");
        KeyboardState two = new KeyboardState("gboard/.Ime;11:other/.Ime;33", "gboard/.Ime", 11);
        KeyboardState filtered = two.available(Set.of("other/.Ime"));
        check(filtered.enabled.equals("other/.Ime;33"), "Remove unavailable keyboards");
        check(filtered.selected.equals("other/.Ime") && filtered.subtype == -1,
                "Choose an available keyboard without using a foreign subtype");
        check(gboard.unionWith(reset).equals("gboard/.Ime;11;22:stock/.Ime"),
                "Enable the saved keyboard before removing the current keyboard");
        check(gboard.available(Set.of("gboard/.Ime", "stock/.Ime")).sameAs(gboard),
                "Restore the exact saved keyboard and explicit languages");
        KeyboardState owner = new KeyboardState("stock/.Ime", "stock/.Ime", -1);
        check(!owner.sameAs(gboard), "Independent profile states can contain different keyboards");
        check(!gboard.sameAs(new KeyboardState(gboard.enabled, gboard.selected, 22)),
                "Remember selected language changes");
        System.out.println("Passed " + checks + " keyboard-state checks.");
    }
}
