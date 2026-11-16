# Privacy and age handling — 1.7.3 preview

The repository layout and Pages main/root setting are retained.
Privacy URL: https://cubicrush.github.io/Notes-to-PDF-Vector-Export/Notes-to-PDF-Vector-Export/docs/privacy.html
Source URL: https://github.com/cubicrush/Notes-to-PDF-Vector-Export/tree/main/Notes-to-PDF-Vector-Export

Support messages and attachments: publisher deletes them one month after the case closes. This does not apply to license-permission evidence. App does not automate mailbox deletion.

## Behaviour
- Neutral, unselected local age bands: under 13, 13–15, 16–17, adult; Skip ads and dismiss keep advertising disabled.
- No UMP request or explicit Ads SDK initialization before an eligible age choice.
- Under 13 and skipped/invalid age values: no rewarded ads; free image PDF remains available.
- Both teen bands: conservative CHILD restricted treatment (stricter than TEEN), UMP under-age flag true, G ad-content ceiling.
- Adults: UMP consent gate and G ad-content ceiling. No cached consent fallback on update errors.
- Debug uses official demo IDs and bypasses UMP only, never the age gate. Debug does NOT validate production consent behaviour.
- Privacy changes invalidate cached/in-flight ads. Old controller callbacks are ignored after age changes.
- Existing RewardGate still requires SDK-earned reward plus dismissal.

## Required device checks before release
1. Fresh install: select none/dismiss/skip; no ad requests, image export works.
2. Under-13 selection: no ads, image export works.
3. Both teen bands: inspect restricted-treatment flags, general-audience ad inventory, and UMP under-age handling on a publisher-configured test build.
4. Adult accept/refuse/withdraw: verify form and SDK requests; no cached ad reappears after withdrawal.
5. Change adult to teen/skip while an ad is loading: old callbacks cannot restore it.
6. Close ad early: no unlock. Earn reward: unlock current note only.
7. Offline startup/consent failure: free image export remains available, no bypass.
8. Open privacy/source links from the installed APK.
9. Recheck age choice after app restart; user can change it from settings.

The public policy remains a pre-release draft until publisher legal-basis/configuration review and these device checks are complete. Do not claim legal certification based on a successful build. Play target audience and AdMob publisher message must be configured separately.

## Build
The Android preview workflow builds and tests on GitHub Actions, then uploads verification reports only; preview APKs must not be published to this public repository. It uses an ephemeral debug signing key; it may not install over earlier previews without uninstalling them. No release key or account secrets are used.
