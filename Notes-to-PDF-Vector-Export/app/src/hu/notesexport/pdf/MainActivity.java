package hu.notesexport.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_NOTE = 100;
    private static final int SAVE_PDF = 101;
    private static final String HOST = "notes-export.invalid";
    private static final String ORIGIN = "https://" + HOST;
    private static final int GREEN = Color.rgb(23, 77, 68);
    private static final int INK = Color.rgb(25, 42, 37);
    private static final int MUTED = Color.rgb(72, 92, 84);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final PdfBridge bridge = new PdfBridge();
    private volatile Uri note;
    private Uri pendingNote;
    private volatile File stagedPdf;
    private WebView engine;
    private LinearLayout root;
    private TextView fileLabel;
    private TextView status;
    private Button choose;
    private Button save, imageExport, vectorExport, privacyButton;
    private AdsController ads;
    private boolean vectorUnlocked, vectorMode, adShowing, destinationOpen;
    private int generation;
    private ProgressBar progress;
    private String pdfName = "Note.pdf";
    private String renderedStatus = "";
    private boolean ready;
    private boolean busy;
    private boolean converted;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(243, 247, 242));
        getWindow().setNavigationBarColor(Color.rgb(243, 247, 242));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildUi();
        if (state != null) {
            restorePdf(state);
            note = state.getParcelable("selected_note");
            vectorUnlocked = state.getBoolean("unlocked");
            vectorMode = state.getBoolean("vector_mode");
            if (converted) save.setText(vectorMode ? "Save vector PDF" : "Save image PDF");
            fileLabel.setText(state.getString("label", "Choose a note to begin."));
        } else if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            pendingNote = sharedNote(getIntent());
            if (pendingNote == null) {
                status.setText("In Samsung Notes, choose Share, then Samsung Notes file (.sdocx), then this app.");
            }
        }
        ads = new AdsController(this, () -> updateButtons());
        setupEngine();
        ads.start();
        updateButtons();
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private GradientDrawable rounded(int fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setBackground(rounded(primary ? GREEN : Color.rgb(222, 238, 231), 14));
        return button;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(243, 247, 242));

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(column);
        add(column, text("Notes to PDF", 36, INK, true), 0);
        add(column, text("Samsung Notes → PDF", 17, GREEN, true), 6);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(rounded(Color.WHITE, 22));
        add(column, card, 26);
        add(card, text("Samsung Notes file", 18, INK, true), 0);
        fileLabel = text("Choose a .sdocx file, or share a note from Samsung Notes.", 15, MUTED, false);
        add(card, fileLabel, 6);
        choose = button("Choose note", true);
        choose.setOnClickListener(v -> pickNote());
        add(card, choose, 18);
        imageExport = button("Convert to image PDF · Free", true);
        imageExport.setOnClickListener(v -> startConversion(false));
        add(card, imageExport, 10);
        vectorExport = button("Watch ad for vector PDF", false);
        vectorExport.setOnClickListener(v -> requestVector());
        add(card, vectorExport, 10);
        save = button("Save PDF", false);
        save.setOnClickListener(v -> pickDestination());
        add(card, save, 10);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(GREEN));
        add(card, progress, 8);
        status = text("Loading PDF engine…", 15, MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(card, status, 12);

        add(column, text("Vector export preserves supported pen strokes and embedded image resolution. Some note elements may render differently.", 15, MUTED, false), 18);
        add(column, text("Conversion happens on your device.", 15, MUTED, false), 8);
        add(column, text("How to use", 21, INK, true), 22);
        add(column, text("1. Open your note in Samsung Notes.\n2. Tap Share and choose Samsung Notes file (.sdocx).\n3. Send it to Notes to PDF: Vector Export.\n4. Choose free image export or unlock vector export, then save your PDF.", 15, MUTED, false), 8);
        add(column, text("Free image export is unlimited and works offline. Complete a rewarded ad to unlock vector export for the selected note.", 15, MUTED, false), 12);
        privacyButton = button("Ad privacy choices", false);
        privacyButton.setOnClickListener(v -> ads.privacy()); add(column, privacyButton, 8);
        Button notices = button("Privacy & open-source notices", false);
        notices.setOnClickListener(v -> showPrivacy()); add(column, notices, 8);
        add(column, text("Independent app. Not affiliated with or endorsed by Samsung. Samsung Notes is a trademark of its owner.", 13, MUTED, false), 18);
        if (BuildConfig.DEBUG) add(column, text("Preview · Google demo ads · No ad revenue.", 13, MUTED, false), 8);
        if (android.os.Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(root);
    }

    private void setupEngine() {
        engine = new WebView(this);
        engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.addView(engine, new LinearLayout.LayoutParams(1, 1));
        engine.getSettings().setJavaScriptEnabled(true);
        engine.getSettings().setAllowFileAccess(false);
        engine.getSettings().setAllowContentAccess(false);
        engine.addJavascriptInterface(bridge, "NotesApp");
        engine.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (!"https".equals(url.getScheme()) || !HOST.equals(url.getHost()) ||
                        !("GET".equals(request.getMethod()))) return missing();
                try {
                    switch (url.getPath()) {
                        case "/engine.html": return asset("engine.html", "text/html", "utf-8");
                        case "/page-size.js": return asset("page-size.js", "text/javascript", "utf-8");
                        case "/pdf-lib.js": return asset("pdf-lib.js", "text/javascript", "utf-8");
                        case "/compose.js": return asset("compose.js", "text/javascript", "utf-8");
                        case "/pako.js": return asset("pako.js", "text/javascript", "utf-8");
                        case "/fontkit.js": return asset("fontkit.js", "text/javascript", "utf-8");
                        case "/Roboto-Regular.ttf": return asset("Roboto-Regular.ttf", "font/ttf", null);
                        case "/fflate.js": return asset("fflate.js", "text/javascript", "utf-8");
                        case "/engine.js": return asset("engine.js", "text/javascript", "utf-8");
                        case "/sdocx.js": return asset("sdocx.js", "text/javascript", "utf-8");
                        case "/sdocx_bg.wasm": return asset("sdocx_bg.wasm", "application/wasm", null);
                        case "/note":
                            Uri selected = note;
                            if (selected == null) return missing();
                            InputStream data = getContentResolver().openInputStream(selected);
                            if (data == null) return missing();
                            return new WebResourceResponse("application/octet-stream", null, 200, "OK",
                                    Collections.singletonMap("Cache-Control", "no-store"), data);
                        default: return missing();
                    }
                } catch (IOException | RuntimeException error) {
                    return missing();
                }
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                root.removeView(view);
                view.destroy();
                engine = null;
                ready = false;
                if (!converted) {
                    bridge.discard();
                    generation++;
                    busy = false;
                }
                setupEngine();
                updateButtons();
                status.setText("The PDF engine is restarting. For large files, try a smaller note.");
                return true;
            }
        });
        engine.loadUrl(ORIGIN + "/engine.html");
    }

    private WebResourceResponse asset(String name, String mime, String encoding) throws IOException {
        return new WebResourceResponse(mime, encoding, getAssets().open(name));
    }

    private WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void updateButtons() {
        boolean idle = ready && !busy && !adShowing && !destinationOpen;
        choose.setEnabled(idle);
        imageExport.setEnabled(idle && note != null);
        vectorExport.setEnabled(idle && note != null);
        boolean entitled = vectorUnlocked;
        vectorExport.setText(entitled ? "Convert to vector PDF" : "Watch ad for vector PDF");
        privacyButton.setVisibility(ads != null && ads.privacyRequired() ? View.VISIBLE : View.GONE);
        privacyButton.setEnabled(!busy && !adShowing);
        choose.setAlpha(choose.isEnabled() ? 1f : .5f);
        save.setEnabled(converted && !busy && !adShowing && !destinationOpen);
        save.setAlpha(save.isEnabled() ? 1f : .5f);
        progress.setVisibility(busy || !ready ? View.VISIBLE : View.GONE);
    }

    private void pickNote() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_NOTE);
    }

    private void pickDestination() {
        if (!converted || busy || adShowing || destinationOpen) return;
        destinationOpen = true; updateButtons();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, pdfName);
        startActivityForResult(intent, SAVE_PDF);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE_PDF) { destinationOpen = false; updateButtons(); }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_NOTE) {
            if (ready) openNote(data.getData());
            else pendingNote = data.getData();
        }
        if (request == SAVE_PDF) writePdf(data.getData());
    }

    static Uri sharedNote(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return null;
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        Uri uri = stream instanceof Uri ? (Uri) stream : null;
        ClipData clip = intent.getClipData();
        if (uri == null && clip != null && clip.getItemCount() == 1) {
            uri = clip.getItemAt(0).getUri();
        }
        return uri != null && "content".equals(uri.getScheme()) ? uri : null;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) { }
        return "Note.sdocx";
    }

    private void openNote(Uri uri) {
        if (!ready || busy || adShowing || destinationOpen) return;
        String name = displayName(uri);
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".sdoc")) {
            new AlertDialog.Builder(this)
                    .setMessage("Open the legacy .sdoc file in Samsung Notes and save it as .sdocx.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        bridge.discard();
        note = uri;
        pdfName = name.replaceFirst("(?i)\\.sdocx$", "") + ".pdf";
        fileLabel.setText(name);
        vectorUnlocked = false;
        converted = false;
        generation++;
        status.setText("Choose image or vector export.");
        updateButtons();
    }

    private void requestVector() {
        if (!ready || busy || adShowing || note == null) return;
        if (vectorUnlocked) { startConversion(true); return; }
        new AlertDialog.Builder(this).setTitle("Unlock vector export")
            .setMessage("Complete a rewarded ad to unlock vector export for this note. Closing it before earning the reward will not unlock export. Free image export remains available.")
            .setNegativeButton("Cancel", null).setPositiveButton("Watch ad", (dialog, which) -> {
                if (busy || adShowing || note == null) return;
                adShowing = true; updateButtons();
                ads.show((earned, message) -> {
                    adShowing = false; status.setText(message);
                    if (earned) { vectorUnlocked = true; startConversion(true); }
                    updateButtons();
                });
            }).show();
    }

    private void startConversion(boolean vector) {
        if (!ready || busy || note == null || adShowing || destinationOpen) return;
        if (vector && !vectorUnlocked) return;
        bridge.discard(); generation++;
        vectorMode = vector; busy = true; converted = false;
        pdfName = displayName(note).replaceFirst("(?i)\\.sdocx$", "") + (vector ? "-vector.pdf" : "-image.pdf");
        status.setText("Converting note…"); updateButtons();
        engine.evaluateJavascript("globalThis.exportNote()", null);
    }

    private void conversionFinished() {
        if (vectorMode) { converted = true; busy = false; save.setText("Save vector PDF"); updateButtons(); status.setText(renderedStatus); return; }
        File source = stagedPdf;
        int job = generation;
        status.setText("Preparing image PDF…");
        if (engine != null) { root.removeView(engine); engine.destroy(); engine = null; ready = false; }
        io.execute(() -> {
            File target = null;
            try {
                target = File.createTempFile("note-", ".pdf", getCacheDir());
                RasterPdf.convert(source, target);
                File result = target;
                runOnUiThread(() -> {
                    if (isDestroyed() || job != generation) { result.delete(); return; }
                    source.delete(); stagedPdf = result; converted = true; busy = false;
                    save.setText("Save image PDF"); setupEngine(); updateButtons(); status.setText(renderedStatus);
                });
            } catch (IOException | RuntimeException | OutOfMemoryError error) {
                if (target != null) target.delete();
                runOnUiThread(() -> {
                    if (isDestroyed() || job != generation) return;
                    bridge.discard(); busy = false; converted = false;
                    setupEngine(); updateButtons(); status.setText("Image export failed. Try a smaller note. Your original note is unchanged.");
                });
            }
        });
    }

    private void showPrivacy() {
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        labels.add("Privacy policy (preview)");
        actions.add(this::showDataSummary);
        if (!BuildConfig.PRIVACY_URL.isEmpty()) {
            labels.add("Privacy policy");
            actions.add(() -> openLink(BuildConfig.PRIVACY_URL));
        }
        if (ads.privacyRequired()) {
            labels.add("Ad privacy choices");
            actions.add(() -> ads.privacy());
        }
        labels.add("Open-source licenses");
        actions.add(this::showLicenses);
        if (!BuildConfig.SOURCE_URL.isEmpty()) {
            labels.add("Source code");
            actions.add(() -> openLink(BuildConfig.SOURCE_URL));
        }
        new AlertDialog.Builder(this).setTitle("Privacy & open source")
                .setItems(labels.toArray(new String[0]), (dialog, index) -> actions.get(index).run())
                .setNegativeButton("Close", null).show();
    }

    private void showDataSummary() {
        new AlertDialog.Builder(this).setTitle("Privacy policy (preview)")
                .setMessage("Publisher: Balog Gergely Sándor. Privacy contact: alogergely@gmail.com. The public privacy policy and remaining disclosures must be completed before publication.\n\nNotes are processed on your device. The app reads the files you select or share and writes PDFs to the location you choose. Temporary PDFs are stored in the app cache. You can clear that cache in Android settings. Saved PDFs remain at the location you chose."
                        + "\n\nGoogle advertising services may process device and ad interaction data. Use Ad privacy choices when available to change your preferences. Free image export works offline."
                        + "\n\nThis is an independent app and is not affiliated with Samsung.")
                .setPositiveButton("OK", null).show();
    }

    private void showLicenses() {
        String[] names = {"sdocx (GPL-3.0)", "pdf-lib", "fontkit", "fflate", "pako", "Roboto"};
        String[] files = {"LICENSE", "PDF-LIB-LICENSE.md", "FONTKIT-NOTICE.txt",
                "FFLATE-LICENSE", "PAKO-LICENSE", "Roboto-LICENSE.txt"};
        new AlertDialog.Builder(this).setTitle("Open-source licenses")
                .setItems(names, (dialog, index) -> showLicense(names[index], files[index]))
                .setNegativeButton("Close", null).show();
    }

    private void showLicense(String title, String filename) {
        String content;
        try (InputStream input = getAssets().open(filename);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            content = new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException error) {
            content = "This license text could not be opened.";
        }
        new AlertDialog.Builder(this).setTitle(title).setMessage(content)
                .setPositiveButton("OK", null).show();
    }

    private void openLink(String url) {
        if (!url.startsWith("https://")) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (RuntimeException error) { status.setText("No browser is available to open this link."); }
    }

    private void writePdf(Uri destination) {
        File pdf = stagedPdf;
        if (!converted || pdf == null) return;
        busy = true;
        status.setText("Save PDF…");
        updateButtons();
        io.execute(() -> {
            try (InputStream from = new FileInputStream(pdf);
                 OutputStream to = getContentResolver().openOutputStream(destination, "wt")) {
                if (to == null) throw new IOException("The destination is not writable.");
                byte[] buffer = new byte[65536];
                int read;
                while ((read = from.read(buffer)) != -1) to.write(buffer, 0, read);
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false;
                    updateButtons();
                    status.setText("Saving failed. Please choose another destination.");
                });
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false;
                updateButtons();
                status.setText("PDF saved.\n" + renderedStatus);
            });
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("selected_note", pendingNote != null ? pendingNote : note);
        state.putBoolean("unlocked", vectorUnlocked);
        state.putBoolean("vector_mode", vectorMode);
        state.putString("label", fileLabel.getText().toString());
        File pdf = stagedPdf;
        if (converted && pdf != null) {
            state.putString("pdf", pdf.getName());
            state.putString("name", pdfName);
            state.putString("label", fileLabel.getText().toString());
            state.putString("status", renderedStatus);
        }
    }

    private void restorePdf(Bundle state) {
        String name = state.getString("pdf", "");
        if (!name.matches("note-[0-9]+\\.pdf")) return;
        File candidate = new File(getCacheDir(), name);
        if (!candidate.isFile()) return;
        stagedPdf = candidate;
        converted = true;
        pdfName = state.getString("name", pdfName);
        renderedStatus = state.getString("status", "");
        fileLabel.setText(state.getString("label", "Note"));
        status.setText(renderedStatus);
    }

    @Override protected void onDestroy() {
        generation++;
        if (ads != null) ads.close();
        if (engine != null) {
            root.removeView(engine);
            engine.destroy();
        }
        io.shutdownNow();
        if (isFinishing() || !converted) bridge.discard();
        super.onDestroy();
    }

    private final class PdfBridge {
        private FileOutputStream stream;

        @JavascriptInterface public void ready() {
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                ready = true;
                updateButtons();
                if (pendingNote != null) {
                    Uri uri = pendingNote;
                    pendingNote = null;
                    openNote(uri);
                } else if (status.getText().toString().equals("Loading PDF engine…")) {
                    status.setText("Choose a note to begin.");
                }
            });
        }

        @JavascriptInterface public synchronized boolean begin() {
            if (note == null || stagedPdf != null) return false;
            try {
                stagedPdf = File.createTempFile("note-", ".pdf", getCacheDir());
                stream = new FileOutputStream(stagedPdf);
                return true;
            } catch (IOException error) {
                discard();
                return false;
            }
        }

        @JavascriptInterface public synchronized boolean chunk(String base64) {
            if (stream == null || base64.length() > 32768) return false;
            try {
                stream.write(Base64.decode(base64, Base64.NO_WRAP));
                return true;
            } catch (IOException | IllegalArgumentException error) {
                return false;
            }
        }

        @JavascriptInterface public synchronized void finish(int pages, String affected, boolean missingImages) {
            try {
                if (stream == null) throw new IOException("Missing PDF.");
                stream.close();
                stream = null;
            } catch (IOException error) {
                error("Could not prepare the PDF.", error.toString());
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                renderedStatus = pages + " pages ready to save.";
                if (!affected.isEmpty()) {
                    renderedStatus += "\nSome elements may differ or be missing on these pages: " + affected + ".";
                }
                if (missingImages) {
                    renderedStatus += "\nAt least one referenced image is missing or unavailable.";
                }
                conversionFinished();
            });
        }

        @JavascriptInterface public void error(String message, String details) {
            discard();
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                busy = false;
                updateButtons();
                progress.setVisibility(View.GONE);
                status.setText(message);
                new AlertDialog.Builder(MainActivity.this).setTitle(message)
                        .setMessage(details.length() > 1000 ? details.substring(0, 1000) : details)
                        .setPositiveButton("OK", null).show();
            });
        }

        private synchronized void discard() {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) { }
                stream = null;
            }
            if (stagedPdf != null) {
                stagedPdf.delete();
                stagedPdf = null;
            }
        }
    }
}
