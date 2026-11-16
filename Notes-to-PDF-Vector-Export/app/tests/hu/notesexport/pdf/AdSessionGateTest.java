package hu.notesexport.pdf;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdSessionGateTest {
    @Test public void adultMustWaitForConsentUpdate() {
        AdSessionGate gate = new AdSessionGate(AgePolicy.ADULT);
        assertFalse(gate.allows(gate.token()));
        int token = gate.invalidate(); gate.approve(token, true);
        assertTrue(gate.allows(token));
    }
    @Test public void failedConsentDoesNotReusePreviousGrant() {
        AdSessionGate gate = new AdSessionGate(AgePolicy.ADULT);
        int old = gate.invalidate(); gate.approve(old, true);
        int current = gate.invalidate(); gate.approve(current, false);
        assertFalse(gate.allows(old)); assertFalse(gate.allows(current));
    }
    @Test public void consentChangeRejectsLateAdAndOldApproval() {
        AdSessionGate gate = new AdSessionGate(AgePolicy.TEEN_13_15);
        int old = gate.invalidate(); gate.approve(old, true);
        int current = gate.invalidate();
        gate.approve(old, true);
        assertFalse(gate.allows(old)); assertFalse(gate.allows(current));
        gate.approve(current, true);
        assertTrue(gate.allows(current)); assertFalse(gate.allows(old));
    }
    @Test public void closingOnAgeChangeCannotBeUndoneByCallback() {
        AdSessionGate gate = new AdSessionGate(AgePolicy.ADULT);
        int old = gate.invalidate(); gate.approve(old, true);
        gate.close(); gate.approve(old, true);
        assertFalse(gate.allows(old));
        gate.approve(gate.token(), true);
        assertFalse(gate.allows(gate.token()));
    }
    @Test public void demoConsentCannotBypassAgeGate() {
        for (int age : new int[]{AgePolicy.UNKNOWN, AgePolicy.UNDER_13, 9}) {
            AdSessionGate gate = new AdSessionGate(age);
            int token = gate.invalidate(); gate.approve(token, true);
            assertFalse(gate.allows(token));
        }
    }
}
