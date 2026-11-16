package hu.notesexport.pdf;

/** A dismissal is not a reward; one ad attempt can complete only once. */
final class RewardGate {
    private boolean earned, finished;
    void earn() { if (!finished) earned = true; }
    boolean dismiss() { if (finished) return false; finished = true; return earned; }
    void fail() { finished = true; earned = false; }
}
