package app.flytteprotokoll;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Keeps the web app (index.html and friends) up to date without reinstalling the APK.
 *
 * Every release publishes web.json ({"version": n, "shell": m}) and web.zip next to the APK.
 * When the release needs no newer Android shell (m <= SHELL_VERSION) and n is newer than the
 * content in use, web.zip is unpacked into filesDir/www-n and served in place of the bundled
 * assets from then on. Only a newer shell requires installing a new APK.
 */
final class Updater {

    static final String BASE = "https://github.com/" + BuildConfig.UPDATE_REPO + "/releases/latest/download/";
    private static final String PREFS = "content";

    /** Latest release as described by web.json. */
    static final class Release {
        final long version;
        final int shell;

        Release(long version, int shell) {
            this.version = version;
            this.shell = shell;
        }
    }

    private final Context ctx;
    private final SharedPreferences prefs;

    Updater(Context ctx) {
        this.ctx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        dropContentOlderThanApk();
    }

    /** Content version in use: a downloaded one if newer than what the APK bundles. */
    long contentVersion() {
        return Math.max(prefs.getLong("version", 0), BuildConfig.VERSION_CODE);
    }

    /** Directory with downloaded content, or null to use the bundled assets. */
    @Nullable
    File activeDir() {
        long v = prefs.getLong("version", 0);
        if (v <= BuildConfig.VERSION_CODE) return null;
        File dir = new File(ctx.getFilesDir(), "www-" + v);
        return new File(dir, "index.html").isFile() ? dir : null;
    }

    /** After an APK update the bundled content can be newer than the downloaded one. */
    private void dropContentOlderThanApk() {
        if (prefs.getLong("version", 0) <= BuildConfig.VERSION_CODE) {
            prefs.edit().remove("version").apply();
            deleteContentDirsExcept(null);
        }
    }

    static Release fetchRelease() throws Exception {
        JSONObject o = new JSONObject(new String(download(BASE + "web.json"), "UTF-8"));
        return new Release(o.getLong("version"), o.optInt("shell", 1));
    }

    /**
     * Downloads and unpacks web.zip for the given version. Returns true when new content is
     * ready; it is used from the next page load.
     */
    boolean installContent(long version) throws Exception {
        if (version <= contentVersion()) return false;
        File dir = new File(ctx.getFilesDir(), "www-" + version);
        File tmp = new File(ctx.getFilesDir(), "www-" + version + ".tmp");
        deleteRecursive(tmp);
        byte[] zip = download(BASE + "web.zip");
        unzip(zip, tmp);
        if (!new File(tmp, "index.html").isFile()) {
            deleteRecursive(tmp);
            throw new IOException("web.zip has no index.html");
        }
        deleteRecursive(dir);
        if (!tmp.renameTo(dir)) throw new IOException("Could not move new content into place");
        prefs.edit().putLong("version", version).commit();
        deleteContentDirsExcept(dir);
        return true;
    }

    private void deleteContentDirsExcept(@Nullable File keep) {
        File[] all = ctx.getFilesDir().listFiles();
        if (all == null) return;
        for (File f : all) {
            if (f.getName().startsWith("www-") && !f.equals(keep)) deleteRecursive(f);
        }
    }

    static byte[] download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " for " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static void downloadTo(String url, File out) throws IOException {
        out.getParentFile().mkdirs();
        try (OutputStream os = new FileOutputStream(out)) {
            os.write(download(url));
        }
    }

    private static void unzip(byte[] zip, File target) throws IOException {
        String root = target.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            byte[] b = new byte[16384];
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                File f = new File(target, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) throw new IOException("Bad zip entry " + e.getName());
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                f.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(f)) {
                    for (int n; (n = zin.read(b)) > 0; ) os.write(b, 0, n);
                }
            }
        }
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /**
     * Serves /assets/www/* from downloaded content when there is some. Returns null for files
     * it does not have, so the loader falls through to the bundled assets.
     */
    final class ContentPathHandler implements WebViewAssetLoader.PathHandler {
        @Nullable
        @Override
        public WebResourceResponse handle(@NonNull String path) {
            File dir = activeDir();
            if (dir == null) return null;
            try {
                File f = new File(dir, path);
                if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator) || !f.isFile()) return null;
                String ext = MimeTypeMap.getFileExtensionFromUrl(path);
                String mime = "js".equals(ext) ? "text/javascript"
                        : "css".equals(ext) ? "text/css"
                        : "html".equals(ext) ? "text/html"
                        : "json".equals(ext) ? "application/json"
                        : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                return new WebResourceResponse(mime, null, new FileInputStream(f));
            } catch (IOException e) {
                return null;
            }
        }
    }
}
