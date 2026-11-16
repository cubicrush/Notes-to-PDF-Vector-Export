package hu.notesexport.pdf;

/** Rejects cached/late ads from an old consent state or a closed controller. */
final class AdSessionGate {
    private final int age;
    private boolean approved, closed;
    private int revision;
    AdSessionGate(int age) { this.age = AgePolicy.normalize(age); }
    int invalidate() { approved = false; return ++revision; }
    void approve(int token, boolean allowed) {
        if (!closed && token == revision) approved = allowed && AgePolicy.adsEligible(age);
    }
    boolean allows(int token) {
        return !closed && approved && token == revision && AgePolicy.adsEligible(age);
    }
    int token() { return revision; }
    void close() { closed = true; invalidate(); }
}
