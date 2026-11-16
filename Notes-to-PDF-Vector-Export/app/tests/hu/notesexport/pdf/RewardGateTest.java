package hu.notesexport.pdf;
import org.junit.Test;
import static org.junit.Assert.*;
public class RewardGateTest {
    @Test public void earlyCloseDoesNotUnlock() { assertFalse(new RewardGate().dismiss()); }
    @Test public void completedRewardUnlocksOnce() { RewardGate g=new RewardGate();g.earn();assertTrue(g.dismiss());assertFalse(g.dismiss()); }
    @Test public void rewardAfterDismissalDoesNotUnlock() { RewardGate g=new RewardGate();g.dismiss();g.earn();assertFalse(g.dismiss()); }
    @Test public void failedShowCannotUnlock() { RewardGate g=new RewardGate();g.earn();g.fail();assertFalse(g.dismiss()); }
    @Test public void nextAttemptCannotReuseReward() { RewardGate g=new RewardGate();g.earn();assertTrue(g.dismiss());assertFalse(new RewardGate().dismiss()); }
}
