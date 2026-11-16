package me.aap.fermata.addon.web;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import me.aap.fermata.ui.activity.MainActivityDelegate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;

/**
 * Phone-side HomeCar GitHub release checker.
 *
 * - Uses the public GitHub Releases API (no token, password or HA data).
 * - At most once per 24h automatically, manual checks always permitted.
 * - Never executes an installer or opens prompts in a projected CarActivity.
 * - Never blocks the UI or holds the WebView's lifecycle.
 */
public final class HomeCarUpdateChecker {
    private static final String API =
            "https://api.github.com/repos/Riftsan/HomeCar/releases?per_page=20";
    private static final String RELEASE_URL_PREFIX =
            "https://github.com/Riftsan/HomeCar/releases/";
    private static final long INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int TIMEOUT_MS = 8000;
    private static final String PREFS = "homecar_update_check";
    private static final String LAST_CHECK = "last_successful_check";
    private static final Pattern VERSION = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+]([a-zA-Z0-9.\\-]+))?$"
    );
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private HomeCarUpdateChecker() {}

    public static void check(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        MainActivityDelegate.getActivityDelegate(activity).onSuccess(delegate -> {
            if (!delegate.isCarActivityNotMirror()) checkOnPhone(activity, manual);
        });
    }

    private static void checkOnPhone(Activity activity, boolean manual) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Context app = activity.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(LAST_CHECK, 0);
        // Guard against clock changes as well as the ordinary 24-hour limit.
        if (!manual && last > 0 && now >= last && (now - last) < INTERVAL_MS) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        if (manual) Toast.makeText(activity, R.string.home_car_update_checking, Toast.LENGTH_SHORT).show();

        final String current = BuildConfig.VERSION_NAME;
        Thread worker = new Thread(() -> {
            Result result = fetchLatest(current);
            activity.runOnUiThread(() -> {
                RUNNING.set(false);
                if (result.success) prefs.edit().putLong(LAST_CHECK,
                        System.currentTimeMillis()).apply();
                if (activity.isFinishing() || activity.isDestroyed()) return;

                // Background checks update the check time only; never interrupt a dashboard session.
                if (!manual) return;
                if (!result.success) {
                    message(activity, R.string.home_car_update_title,
                            activity.getString(R.string.home_car_update_failed, result.error));
                    return;
                }
                if (result.tag == null) {
                    message(activity, R.string.home_car_update_title,
                            activity.getString(R.string.home_car_update_no_releases));
                    return;
                }
                if (!result.newer) {
                    message(activity, R.string.home_car_update_current,
                            activity.getString(R.string.home_car_update_versions, current, result.tag));
                    return;
                }

                final String url = result.url;
                new MaterialAlertDialogBuilder(HomeCarSettingsSheet.themedContext(activity))
                        .setTitle(R.string.home_car_update_available)
                        .setMessage(activity.getString(R.string.home_car_update_message, current, result.tag))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.home_car_update_open, (d, which) -> {
                            if (url == null || !url.startsWith(RELEASE_URL_PREFIX)) return;
                            try {
                                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (ActivityNotFoundException ignored) {
                                message(activity, R.string.home_car_update_title,
                                        activity.getString(R.string.home_car_update_no_browser));
                            }
                        }).show();
            });
        }, "HomeCar-GitHub-Updates");
        worker.setDaemon(true);
        worker.start();
    }

    private static Result fetchLatest(String installed) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "HomeCar-Android-UpdateChecker");
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return Result.fail("HTTP " + code);
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = stream.read(buf)) != -1) {
                    if (bytes.size() + len > 512 * 1024) return Result.fail("Response too large");
                    bytes.write(buf, 0, len);
                }
            }
            JSONArray releases = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            String bestTag = null;
            String bestUrl = null;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || release.optBoolean("draft", false)) continue;
                String tag = release.optString("tag_name", "");
                if (parsedVersion(tag) == null) continue;
                String url = release.optString("html_url", "");
                if (!url.startsWith(RELEASE_URL_PREFIX)) continue;
                if (bestTag == null || compareVersions(tag, bestTag) > 0) {
                    bestTag = tag;
                    bestUrl = url;
                }
            }
            return Result.ok(bestTag, bestUrl,
                    bestTag != null && compareVersions(bestTag, installed) > 0);
        } catch (Exception ex) {
            return Result.fail(ex.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Returns major, minor, patch, pre-release precedence or null. */
    private static int[] parsedVersion(String version) {
        Matcher match = VERSION.matcher(version.trim());
        if (!match.matches()) return null;
        try {
            int major = Integer.parseInt(match.group(1));
            int minor = Integer.parseInt(match.group(2));
            int patch = Integer.parseInt(match.group(3));
            String suffix = match.group(4);
            int stage = 100000; // A stable release beats all its pre-releases.
            if (suffix != null && !suffix.isEmpty()) {
                String s = suffix.toLowerCase(Locale.ROOT);
                String[] parts = s.split("\\.");
                int n = 0;
                if (parts.length >= 2) {
                    try { n = Math.min(9999, Integer.parseInt(parts[1])); }
                    catch (NumberFormatException ignored) { n = 0; }
                }
                if (s.startsWith("rc")) stage = 50000 + n;
                else if (s.startsWith("beta")) stage = 30000 + n;
                else if (s.startsWith("alpha")) stage = 10000 + n;
                else stage = 1000 + n;
            }
            return new int[] {major, minor, patch, stage};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int compareVersions(String left, String right) {
        int[] a = parsedVersion(left);
        int[] b = parsedVersion(right);
        if (a == null || b == null) return 0;
        for (int i = 0; i < 4; i++) {
            int compared = Integer.compare(a[i], b[i]);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static void message(Activity activity, int title, String description) {
        if (!activity.isFinishing() && !activity.isDestroyed())
            new MaterialAlertDialogBuilder(HomeCarSettingsSheet.themedContext(activity)).setTitle(title)
                    .setMessage(description).setPositiveButton(android.R.string.ok, null).show();
    }

    private static final class Result {
        final boolean success;
        final String tag;
        final String url;
        final boolean newer;
        final String error;

        private Result(boolean success, String tag, String url, boolean newer, String error) {
            this.success = success;
            this.tag = tag;
            this.url = url;
            this.newer = newer;
            this.error = error;
        }

        static Result ok(String tag, String url, boolean newer) {
            return new Result(true, tag, url, newer, null);
        }
        static Result fail(String error) {
            return new Result(false, null, null, false, error);
        }
    }
}
