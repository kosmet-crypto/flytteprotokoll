package app.flytteprotokoll;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs a downloaded APK update without Android's install screen where the phone allows it
 * (Android 12+, the app updating itself). If the phone refuses, Android's usual install screen
 * is opened for the same file instead.
 */
public class QuietInstall extends BroadcastReceiver {

    private static final String EXTRA_APK = "apk";

    /** Hands the APK to PackageInstaller. Call off the main thread. */
    static void start(Context ctx, File apk) throws IOException {
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id);
             InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("update.apk", 0, apk.length())) {
            byte[] b = new byte[65536];
            for (int r; (r = in.read(b)) > 0; ) out.write(b, 0, r);
            session.fsync(out);
            Intent result = new Intent(ctx, QuietInstall.class).putExtra(EXTRA_APK, apk.getAbsolutePath());
            session.commit(PendingIntent.getBroadcast(ctx, id, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE).getIntentSender());
        } catch (IOException | RuntimeException e) {
            installer.abandonSession(id);
            throw e;
        }
    }

    /** Android's normal install screen for the downloaded file. */
    static void openScreen(Context ctx, File apk) {
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".files", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, "Kunne ikke åpne installasjonen", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation")
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String path = intent.getStringExtra(EXTRA_APK);
            if (path != null && new File(path).isFile()) openScreen(ctx, new File(path));
        }
    }
}
