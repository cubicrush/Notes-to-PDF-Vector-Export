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
    private final ConsentInformation consent;
    private final Handler main = new Handler(Looper.getMainLooper());
    private RewardedAd ad;
    private long loadedAt;
    private boolean initializing, initialized, loading, destroyed;
    AdsController(Activity activity, Runnable changed) {
        this.activity = activity; this.changed = changed;
        consent = UserMessagingPlatform.getConsentInformation(activity);
    }
    void start() {
        // Demo builds have no publisher UMP message. Never use this bypass with live IDs.
        if (BuildConfig.DEBUG) { prepare(); return; }
        consent.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(),
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> { prepare(); changed.run(); }),
            error -> { prepare(); changed.run(); });
        prepare();
    }
    private void prepare() {
        if (destroyed || activity.isDestroyed() || !(BuildConfig.DEBUG || consent.canRequestAds())) return;
        if (!initialized) {
            if (initializing) return;
            initializing = true;
            new Thread(() -> MobileAds.initialize(activity.getApplicationContext(), result -> main.post(() -> {
                initialized = true; initializing = false; prepare();
            })), "ads-init").start();
            return;
        }
        if (loading || available()) return;
        loading = true;
        RewardedAd.load(activity, BuildConfig.REWARDED_AD_UNIT, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd value) {
                loading = false;
                if (destroyed || !(BuildConfig.DEBUG || consent.canRequestAds())) return;
                ad = value; loadedAt = SystemClock.elapsedRealtime(); changed.run();
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { loading = false; ad = null; if (!destroyed) changed.run(); }
        });
    }
    boolean available() {
        return !destroyed && (BuildConfig.DEBUG || consent.canRequestAds()) && ad != null && SystemClock.elapsedRealtime() - loadedAt < 55 * 60 * 1000L;
    }
    boolean privacyRequired() {
        return consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }
    void privacy() {
        if (destroyed) return;
        ad = null;
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> { prepare(); changed.run(); });
    }
    void show(Result result) {
        if (!available()) { prepare(); result.finish(false, "No rewarded ad is available. Try again online, or export an image PDF for free."); return; }
        RewardedAd showing = ad; ad = null;
        RewardGate gate = new RewardGate();
        showing.setFullScreenContentCallback(new FullScreenContentCallback() {
            private boolean delivered;
            private void finish(boolean reward, String message) {
                if (delivered) return; delivered = true;
                if (!destroyed && !activity.isDestroyed()) { result.finish(reward, message); prepare(); }
            }
            @Override public void onAdDismissedFullScreenContent() {
                boolean earned = gate.dismiss();
                finish(earned, earned ? "Vector export unlocked for this note." : "Ad closed before the reward was earned. Free image export remains available.");
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { gate.fail(); finish(false, "The ad could not be shown. Try free image export."); }
        });
        showing.show(activity, reward -> gate.earn());
    }
    void close() { destroyed = true; ad = null; }
}
