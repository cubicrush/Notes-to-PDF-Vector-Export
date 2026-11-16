# Changes from upstream and preview status

This project embeds sdocx in an Android WebView with a Java bridge. Android integration adds incoming Samsung Notes shares, document selection/saving, PDF composition adjustments, image PDF rendering, English instructions, Google rewarded ads and UMP consent handling. The converter assets were carried forward from the accepted 1.6 preview; translating error messages did not rebuild the WASM module.

The 1.7.2 preview removed the Pro purchase path and Billing dependency. The subsequent source-only update added the publisher's name and contact email.

16 November 2026: prepared this repository snapshot, added the public source URL, removed obsolete publication documents, documented the author's SDK permission, added a draft privacy page and removed bundled development signing keys. Debug signing now uses Gradle's locally generated debug key. Conversion code and assets are unchanged by this repository-preparation step.

This snapshot has no newly distributed binary. Before a production release, verify the converter rebuild procedure and source-to-binary mapping, record the exact release tag, and publish the corresponding source with that release. Prior build results do not certify these publication edits or actual Android device behaviour.
