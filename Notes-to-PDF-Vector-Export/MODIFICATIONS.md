# Changes from upstream and preview status

This project embeds sdocx in an Android WebView with a Java bridge. Android integration adds incoming Samsung Notes shares, document selection/saving, PDF composition adjustments, image PDF rendering, English instructions, Google rewarded ads and UMP consent handling. The converter assets were carried forward from the accepted 1.6 preview; translating error messages did not rebuild the WASM module.

The 1.7.2 preview removed the Pro purchase path and Billing dependency. The subsequent source-only update added the publisher's name and contact email.

16 November 2026: prepared this repository snapshot, added the public source URL, removed obsolete publication documents, documented the author's SDK permission, added a draft privacy page and removed bundled development signing keys. Debug signing now uses Gradle's locally generated debug key. Conversion code and assets are unchanged by this repository-preparation step.

19 November 2026: version 1.7.4 adds a support email action and an offline privacy summary, and documents the one-month retention period after a support case closes. Release bundles can be signed separately using the publisher's upload key.

20 November 2026: version 1.7.5 reports WebView startup failures on devices whose built-in browser cannot load the converter. The update notice also works when the module itself cannot be parsed.
