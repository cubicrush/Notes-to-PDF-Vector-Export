package hu.notesexport.pdf;
import org.junit.Test;
import static org.junit.Assert.*;
public class AgePolicyTest {
    @Test public void noAdsBeforeChoiceOrForUnder13() {
        for (int age : new int[]{-10, AgePolicy.UNKNOWN, AgePolicy.UNDER_13, 4, 100})
            assertFalse(AgePolicy.adsEligible(age));
    }
    @Test public void bothTeenBandsHaveRestrictedAds() {
        for (int age : new int[]{AgePolicy.TEEN_13_15, AgePolicy.TEEN_16_17}) {
            assertTrue(AgePolicy.adsEligible(age)); assertTrue(AgePolicy.restricted(age));
        }
        assertFalse(AgePolicy.restricted(AgePolicy.ADULT));
    }
    @Test public void invalidStoredAgeCannotBecomeAdult() {
        assertEquals(AgePolicy.UNKNOWN, AgePolicy.normalize(Integer.MAX_VALUE));
        assertTrue(AgePolicy.restricted(Integer.MAX_VALUE));
    }
}
