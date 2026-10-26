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
        Set<String> systemKeyboards = Set.of("stock/.Ime");
        check(reset.addsSystemKeyboard(gboard, systemKeyboards),
                "A late stock-keyboard reset must not replace the saved selection");
        check(!gboard.addsSystemKeyboard(gboard, systemKeyboards),
                "Restoration notifications must not start another restoration");
        check(!reset.addsSystemKeyboard(reset, systemKeyboards),
                "An intentionally saved system keyboard remains allowed");
        check(!two.addsSystemKeyboard(gboard, systemKeyboards),
                "New third-party keyboards remain ordinary user changes");
        check(!gboard.addsSystemKeyboard(reset, systemKeyboards),
                "Disabling the system keyboard must update the snapshot");
        check(!reset.addsSystemKeyboard(null, systemKeyboards),
                "The first activation must capture the user's current selection");
        KeyboardState subtypeChange = new KeyboardState("gboard/.Ime;22", "gboard/.Ime", 22);
        check(!subtypeChange.addsSystemKeyboard(gboard, systemKeyboards),
                "Language changes must still be remembered");
        check(gboard.preservingInput(subtypeChange).sameAs(subtypeChange),
                "Restoration must preserve the current language and enabled languages");
        KeyboardState switchedLanguage = new KeyboardState(gboard.enabled, gboard.selected, 22);
        check(gboard.preservingInput(switchedLanguage).sameAs(switchedLanguage),
                "Changing the language during startup must not trigger a reset");
        KeyboardState lateReset = new KeyboardState("stock/.Ime:" + switchedLanguage.enabled,
                switchedLanguage.selected, switchedLanguage.subtype);
        check(gboard.preservingInput(lateReset).sameAs(switchedLanguage),
                "Removing a re-enabled stock keyboard must preserve the user's language");
        check(gboard.preservingInput(reset).sameAs(gboard),
                "Switch away from an excluded keyboard using the saved selection");
        KeyboardState otherSelected = new KeyboardState(two.enabled, "other/.Ime", 33);
        check(two.preservingInput(otherSelected).sameAs(otherSelected),
                "Do not undo a switch between allowed keyboards");
        check(filtered.preservingInput(reset).sameAs(filtered),
                "Preserve the available fallback when the selected saved keyboard is missing");
        KeyboardState repeated = gboard;
        for (int i = 0; i < 3; i++) {
            KeyboardState changed = new KeyboardState(gboard.enabled, gboard.selected, i % 2 == 0 ? 22 : 11);
            repeated = repeated.preservingInput(changed);
            check(repeated.sameAs(changed), "Every restoration attempt must respect a new language choice");
        }
        System.out.println("Passed " + checks + " keyboard-state checks.");
    }
}
