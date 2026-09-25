package app.flytteprotokoll;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;


import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Hosts the Flytteprotokoll web app (bundled in assets/www) in a full-screen WebView.
 * Pages are served from https://appassets.androidplatform.net so IndexedDB and
 * crypto.subtle behave like on a normal https site.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_PICK_FILE = 1;
    private static final int REQ_SAVE_FILE = 2;
    private static final int REQ_CAMERA = 3;

    private WebView webView;
    private ValueCallback<Uri[]> pendingPick;
    private Uri pendingCameraUri;
    private byte[] pendingSaveBytes;
    private String pendingSaveDoneMsg;
    private Updater updater;
    private volatile boolean contentReady;
    private File pendingApk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        updater = new Updater(this);
        // Downloaded content (if any) first; files it lacks fall through to the bundled assets.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/www/", updater.new ContentPathHandler())
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFF1F5F9);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true); // photos come back as content:// URIs
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new Bridge(), "FlytteAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost())) return false;
                // Anything outside the app opens in the browser.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingPick != null) pendingPick.onReceiveValue(null);
                pendingPick = callback;
                boolean images = false;
                for (String t : params.getAcceptTypes()) if (t.startsWith("image")) images = true;
                if (images && params.isCaptureEnabled() && openCamera()) return true;

                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                // Photos and the logo are images; a backup file may have any MIME type
                // depending on the file manager, so let the page validate it.
                i.setType(images ? "image/*" : "*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE)
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(i, REQ_PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingPick = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
    }

    /** Starts the camera app, writing the photo to a cache file we share through FileProvider. */
    private boolean openCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            dir.mkdirs();
            File f = new File(dir, "foto_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
            return true;
        } catch (Exception e) {
            pendingCameraUri = null;
            return false;
        }
    }

    /* ---------- updates ---------- */

    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;
    private static final long APK_PROMPT_INTERVAL = 12 * 60 * 60 * 1000L;

    /**
     * Reads web.json from the latest release. New web content is downloaded and applied
     * silently; only a release that needs a newer Android shell asks to install a new APK.
     * The automatic check runs at most every few hours; the button in Innstillinger always runs.
     */
    private void checkForUpdate(final boolean manual) {
        final SharedPreferences prefs = getSharedPreferences("update", MODE_PRIVATE);
        final long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong("lastCheck", 0) < CHECK_INTERVAL) return;
        prefs.edit().putLong("lastCheck", now).apply();
        if (manual) toast("Ser etter oppdatering…");

        new Thread(() -> {
            try {
                Updater.Release r = Updater.fetchRelease();
                if (r.shell > BuildConfig.SHELL_VERSION) {
                    if (manual || now - prefs.getLong("lastApkPrompt", 0) > APK_PROMPT_INTERVAL) {
                        prefs.edit().putLong("lastApkPrompt", now).apply();
                        runOnUiThread(this::showApkDialog);
                    }
                } else if (updater.installContent(r.version)) {
                    contentReady = true;
                    runOnUiThread(this::reloadIfIdle);
                } else if (manual) {
                    toast("Du har nyeste versjon");
                }
            } catch (Exception e) {
                // No network or unexpected response: the automatic check tries again later.
                if (manual) toast("Kunne ikke sjekke. Er du på nett?");
            }
        }).start();
    }

    /** Loads new content once the page says nothing is being edited (the protocol list is showing). */
    private void reloadIfIdle() {
        if (!contentReady) return;
        webView.evaluateJavascript("typeof fpCanReload === 'function' && fpCanReload()", idle -> {
            if (!"true".equals(idle) || !contentReady) return;
            contentReady = false;
            webView.reload();
            toast("Appen er oppdatert");
        });
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private void showApkDialog() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Ny versjon av appen")
                .setMessage("En ny versjon av Flytteprotokoll må installeres. Den lastes ned her, så trykker du Installer. Protokollene dine blir liggende.")
                .setPositiveButton("Last ned", (d, w) -> downloadApk())
                .setNegativeButton("Senere", null)
                .show();
    }

    private void downloadApk() {
        toast("Laster ned ny versjon…");
        new Thread(() -> {
            try {
                File f = new File(new File(getCacheDir(), "apk"), "flytteprotokoll.apk");
                Updater.downloadTo(Updater.BASE + "flytteprotokoll.apk", f);
                runOnUiThread(() -> installApk(f));
            } catch (Exception e) {
                toast("Nedlastingen feilet. Prøv igjen senere.");
            }
        }).start();
    }

    /** Opens the system installer. The first time, Android asks to allow installs from this app. */
    private void installApk(File f) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            pendingApk = f;
            Toast.makeText(this, "Tillat installasjon fra Flytteprotokoll, og gå tilbake", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                pendingApk = null;
            }
            return;
        }
        pendingApk = null;
        if (Build.VERSION.SDK_INT < 31) {
            QuietInstall.openScreen(this, f);
            return;
        }
        // Android 12+: try without the install screen; QuietInstall falls back to it if refused.
        new Thread(() -> {
            try {
                QuietInstall.start(this, f);
            } catch (Exception e) {
                runOnUiThread(() -> QuietInstall.openScreen(this, f));
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingApk != null && getPackageManager().canRequestPackageInstalls()) installApk(pendingApk);
        if (webView != null) {
            reloadIfIdle();
            checkForUpdate(false);
        }
    }

    /* ---------- saving and sharing files ---------- */

    /** Asks the user where to save (system file picker), then writes the bytes there. */
    private void saveAs(String name, String mime, byte[] bytes, String doneMsg) {
        pendingSaveBytes = bytes;
        pendingSaveDoneMsg = doneMsg;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_SAVE_FILE);
        } catch (ActivityNotFoundException e) {
            pendingSaveBytes = null;
            Toast.makeText(this, "Fant ingen app for å lagre filer", Toast.LENGTH_LONG).show();
        }
    }

    /** Writes the PDF to the cache and opens the Android share sheet (e-post, Teams, Drive…). */
    private void share(String name, byte[] bytes, String subject, String text) {
        try {
            File dir = new File(getCacheDir(), "shared");
            dir.mkdirs();
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(bytes);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, subject != null && !subject.isEmpty() ? subject : name.replace(".pdf", "").replace('_', ' '));
            if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
            i.setClipData(ClipData.newRawUri(name, uri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Del protokoll"));
        } catch (Exception e) {
            Toast.makeText(this, "Kunne ikke dele PDF", Toast.LENGTH_LONG).show();
        }
    }

    /** Methods index.html can call as window.FlytteAndroid.*. */
    private class Bridge {
        @JavascriptInterface
        public String getVersion() {
            return BuildConfig.VERSION_NAME + " (innhold " + updater.contentVersion() + ")";
        }

        /** The page calls this when it goes back to the protocol list, a safe moment to apply new content. */
        @JavascriptInterface
        public void onIdle() {
            runOnUiThread(MainActivity.this::reloadIfIdle);
        }

        @JavascriptInterface
        public void checkForUpdate() {
            runOnUiThread(() -> MainActivity.this.checkForUpdate(true));
        }

        /** Saves a backup; WebView cannot download blob: URLs. */
        @JavascriptInterface
        public void saveFile(final String name, final String text) {
            final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            runOnUiThread(() -> saveAs(name, "application/json", bytes, "Sikkerhetskopi lagret"));
        }

        @JavascriptInterface
        public void savePdf(final String name, final String base64) {
            final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(() -> saveAs(name, "application/pdf", bytes, "PDF lagret"));
        }

        @JavascriptInterface
        public void sharePdf(final String name, final String base64, final String subject, final String text) {
            final byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            runOnUiThread(() -> share(name, bytes, subject, text));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean ok = resultCode == RESULT_OK;

        if (requestCode == REQ_CAMERA && pendingPick != null) {
            pendingPick.onReceiveValue(ok && pendingCameraUri != null ? new Uri[]{pendingCameraUri} : null);
            pendingPick = null;
            pendingCameraUri = null;
        } else if (requestCode == REQ_PICK_FILE && pendingPick != null) {
            Uri[] result = null;
            if (ok && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null) {
                    result = new Uri[clip.getItemCount()];
                    for (int i = 0; i < result.length; i++) result[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            pendingPick.onReceiveValue(result);
            pendingPick = null;
        } else if (requestCode == REQ_SAVE_FILE) {
            byte[] bytes = pendingSaveBytes;
            pendingSaveBytes = null;
            Uri uri = ok && data != null ? data.getData() : null;
            if (uri == null || bytes == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(bytes);
                Toast.makeText(this, pendingSaveDoneMsg, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Kunne ikke lagre filen", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Back goes from a protocol or settings to the list; only on the list does it leave the app. */
    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("typeof fpBack === 'function' && fpBack()", handled -> {
            if (!"true".equals(handled)) super.onBackPressed();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }
}
