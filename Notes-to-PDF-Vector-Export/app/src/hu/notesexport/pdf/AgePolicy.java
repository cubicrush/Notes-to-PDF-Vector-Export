package hu.notesexport.pdf;

/** Local age bands; never store a birth date. Restricted treatment for every minor. */
final class AgePolicy {
    static final int UNKNOWN = -1, UNDER_13 = 0, TEEN_13_15 = 1, TEEN_16_17 = 2, ADULT = 3;
    static int normalize(int value) {
        return value >= UNDER_13 && value <= ADULT ? value : UNKNOWN;
    }
    static boolean adsEligible(int value) {
        int age = normalize(value);
        return age == TEEN_13_15 || age == TEEN_16_17 || age == ADULT;
    }
    static boolean restricted(int value) { return normalize(value) != ADULT; }
    private AgePolicy() {}
}
