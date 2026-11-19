package hu.notesexport.pdf;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.rewarded.*;
import com.google.android.ump.*;

final class AdsController {
    interface Result { void finish(boolean earned, String message); }
    private final Activity activity;
    private final Runnable changed;
    private final int age;
    private final AdSessionGate gate;
    private ConsentInformation consent;
    private final Handler main = new Handler(Looper.getMainLooper());
    private RewardedAd ad;
    private long loadedAt;
    private int adToken, loadToken = -1;
    private boolean initializing, initialized, destroyed, started;

    AdsController(Activity activity, Runnable changed, int age) {
        this.activity = activity; this.changed = changed;
        this.age = AgePolicy.normalize(age);
        gate = new AdSessionGate(this.age);
        // Do not even obtain UMP until the local age gate permits advertising.
    }
    void start() {
        if (destroyed || started || !AgePolicy.adsEligible(age)) return;
        started = true;
        MobileAds.setRequestConfiguration(MobileAds.getRequestConfiguration().toBuilder()
            // CHILD is deliberately stricter than TEEN for all under-18 users.
            .setAgeRestrictedTreatment(AgePolicy.restricted(age)
                ? AgeRestrictedTreatment.CHILD : AgeRestrictedTreatment.UNSPECIFIED)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build());
        int token = gate.invalidate();
        // Official demo IDs only. Age/content restrictions still apply in debug.
        if (BuildConfig.DEBUG) { gate.approve(token, true); prepare(); changed.run(); return; }
        consent = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(AgePolicy.restricted(age)).build();
        consent.requestConsentInfoUpdate(activity, parameters, () -> {
            if (destroyed || token != gate.token()) return;
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                if (destroyed || token != gate.token()) return;
                gate.approve(token, error == null && consent.canRequestAds());
                prepare(); changed.run();
            });
        }, error -> {
            // Do not use a cached adult grant after a failed age-specific update.
            if (!destroyed && token == gate.token()) {
                gate.approve(token, false); changed.run();
            }
        });
    }
    private boolean permitted() {
        return !destroyed && !activity.isDestroyed() && gate.allows(gate.token())
            && (BuildConfig.DEBUG || (consent != null && consent.canRequestAds()));
    }
    private void prepare() {
        if (!permitted()) return;
        if (!initialized) {
            if (initializing) return;
            initializing = true;
            // Entry and callback run on the UI thread, so profile changes cannot
            // interleave between the permission check and SDK initialization.
            MobileAds.initialize(activity.getApplicationContext(), result -> main.post(() -> {
                if (destroyed) return;
                initialized = true; initializing = false; prepare();
            }));
            return;
        }
        if (loadToken == gate.token() || available()) return;
        final int token = gate.token();
        loadToken = token;
        RewardedAd.load(activity, BuildConfig.REWARDED_AD_UNIT, new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd value) {
                    if (loadToken == token) loadToken = -1;
                    if (!permitted() || !gate.allows(token)) return;
                    ad = value; adToken = token;
                    loadedAt = SystemClock.elapsedRealtime(); changed.run();
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    if (loadToken == token) loadToken = -1;
                    if (destroyed || token != gate.token()) return;
                    ad = null; changed.run();
                }
            });
    }
    boolean available() {
        return permitted() && ad != null && gate.allows(adToken)
            && SystemClock.elapsedRealtime() - loadedAt < 55 * 60 * 1000L;
    }
    boolean privacyRequired() {
        return !destroyed && !BuildConfig.DEBUG && consent != null
            && consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }
    void privacy() {
        if (!privacyRequired()) return;
        ad = null;
        int token = gate.invalidate(); loadToken = -1;
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (destroyed || token != gate.token()) return;
            gate.approve(token, error == null && consent.canRequestAds());
            prepare(); changed.run();
        });
        changed.run();
    }
    void show(Result result) {
        if (!available()) {
            prepare();
            result.finish(false, AgePolicy.adsEligible(age)
                ? "No rewarded ad is available. Try again online, or export an image PDF for free."
                : "Rewarded ads are unavailable for this age setting. Free image PDF export is available.");
            return;
        }
        RewardedAd showing = ad; ad = null;
        final int token = gate.token();
        RewardGate rewardGate = new RewardGate();
        showing.setFullScreenContentCallback(new FullScreenContentCallback() {
            private boolean delivered;
            private void finish(boolean reward, String message) {
                if (delivered) return;
                delivered = true;
                if (!destroyed && !activity.isDestroyed() && gate.allows(token)) {
                    result.finish(reward, message); prepare();
                }
            }
            @Override public void onAdDismissedFullScreenContent() {
                boolean earned = rewardGate.dismiss();
                finish(earned, earned ? "Vector export unlocked for this note."
                    : "Ad closed before the reward was earned. Free image export remains available.");
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                rewardGate.fail(); finish(false, "The ad could not be shown. Try free image export.");
            }
        });
        showing.show(activity, reward -> {
            if (!destroyed && gate.allows(token)) rewardGate.earn();
        });
    }
    void close() { destroyed = true; gate.close(); ad = null; }
}
