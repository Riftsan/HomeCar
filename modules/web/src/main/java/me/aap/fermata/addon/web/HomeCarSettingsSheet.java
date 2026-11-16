package me.aap.fermata.addon.web;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.net.URI;
import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.function.Consumer;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.security.ClientCertificateManager;
import me.aap.fermata.security.ClientCertificateOrigin;

/** Native phone-only setup. Certificate readiness is never presented as a connection test. */
final class HomeCarSettingsSheet {
    private final Activity activity;
    private final Context context;
    private final BottomSheetDialog dialog;
    private final View content;
    private final TextInputLayout serverLayout;
    private final TextInputEditText serverInput;
    private final TextView certificateStatus;
    private final TextView certificateAlias;
    private final TextView certificateExpiry;
    private final TextView certificateOrigin;
    private final TextView certificateDescription;
    private final TextView notice;
    private final MaterialButton chooseCertificate;
    private final MaterialButton forgetCertificate;
    private final MaterialButton saveServer;
    private final MaterialButton reloadDashboard;
    private final Consumer<String> openServer;
    private final Runnable reload;
    private int requestGeneration;
    private boolean choosing;
    private boolean opening;
    @Nullable private Runnable pendingInspection;
    @Nullable private AlertDialog forgetConfirmation;

    HomeCarSettingsSheet(Activity activity, String savedUrl, @Nullable String currentUrl,
                         Consumer<String> openServer, Runnable reload) {
        this.activity = activity;
        this.context = themedContext(activity);
        this.openServer = openServer;
        this.reload = reload;
        dialog = new BottomSheetDialog(context, R.style.ThemeOverlay_HomeCar_BottomSheet);
        content = LayoutInflater.from(dialog.getContext()).inflate(R.layout.home_car_settings_sheet, null);
        dialog.setContentView(content);
        serverLayout = content.findViewById(R.id.homeCarServerInputLayout);
        serverInput = content.findViewById(R.id.homeCarServerInput);
        certificateStatus = content.findViewById(R.id.homeCarCertificateStatus);
        certificateAlias = content.findViewById(R.id.homeCarCertificateAlias);
        certificateExpiry = content.findViewById(R.id.homeCarCertificateExpiry);
        certificateOrigin = content.findViewById(R.id.homeCarCertificateOrigin);
        certificateDescription = content.findViewById(R.id.homeCarCertificateDescription);
        notice = content.findViewById(R.id.homeCarSettingsNotice);
        chooseCertificate = content.findViewById(R.id.homeCarChooseCertificate);
        forgetCertificate = content.findViewById(R.id.homeCarForgetCertificate);
        saveServer = content.findViewById(R.id.homeCarSaveServer);
        reloadDashboard = content.findViewById(R.id.homeCarReloadDashboard);

        TextView currentServer = content.findViewById(R.id.homeCarCurrentServer);
        String displayedOrigin = displayOrigin(currentUrl);
        currentServer.setText(displayedOrigin == null
                ? context.getString(R.string.home_car_dashboard_not_open)
                : context.getString(R.string.home_car_current_dashboard, displayedOrigin));
        TextView version = content.findViewById(R.id.homeCarAppVersion);
        version.setText(context.getString(R.string.home_car_version, BuildConfig.VERSION_NAME));
        serverInput.setText(savedUrl);
        content.findViewById(R.id.homeCarCloseSettings).setOnClickListener(v -> dismiss());
        saveServer.setOnClickListener(v -> saveAndOpen());
        reloadDashboard.setOnClickListener(v -> reload());
        chooseCertificate.setOnClickListener(v -> chooseCertificate());
        forgetCertificate.setOnClickListener(v -> forgetCertificate());
        content.findViewById(R.id.homeCarCheckUpdates).setOnClickListener(v ->
                HomeCarUpdateChecker.check(activity, true));
        serverInput.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO && action != EditorInfo.IME_ACTION_DONE) return false;
            saveAndOpen();
            return true;
        });
        serverInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                serverLayout.setError(null);
                notice.setVisibility(View.GONE);
                requestGeneration++;
                if (pendingInspection != null) content.removeCallbacks(pendingInspection);
                chooseCertificate.setEnabled(false);
                forgetCertificate.setVisibility(View.GONE);
                certificateAlias.setVisibility(View.GONE);
                certificateExpiry.setVisibility(View.GONE);
                certificateStatus.setText(R.string.home_car_certificate_checking);
                pendingInspection = HomeCarSettingsSheet.this::inspectCertificate;
                content.postDelayed(pendingInspection, 350);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        dialog.setOnDismissListener(ignored -> {
            requestGeneration++;
            if (pendingInspection != null) content.removeCallbacks(pendingInspection);
            if (forgetConfirmation != null) forgetConfirmation.dismiss();
        });
        dialog.setOnShowListener(ignored -> {
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setMaxWidth(dp(640));
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            inspectCertificate();
        });
        ViewCompat.setAccessibilityPaneTitle(content, context.getString(R.string.home_car_settings_title));
        // The whole form scrolls, including actions, so the keyboard and large text cannot hide them.
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int bottom = Math.max(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottom);
            return insets;
        });
    }

    /** The native setup follows the system appearance, independently of the dashboard/app theme. */
    static Context themedContext(Activity activity) {
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
        ContextThemeWrapper themed = new ContextThemeWrapper(activity, R.style.Theme_HomeCar_Settings);
        themed.applyOverrideConfiguration(configuration);
        return themed;
    }

    void show() {
        if (!activity.isFinishing() && !activity.isDestroyed()) dialog.show();
    }

    boolean isShowing() {
        return dialog.isShowing();
    }

    void dismiss() {
        dialog.dismiss();
    }

    private boolean active() {
        return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed();
    }

    @Nullable private String serverUrl(boolean showError) {
        String raw = serverInput.getText() == null ? "" : serverInput.getText().toString().trim();
        String url = ClientCertificateOrigin.normalizeServerUrl(raw);
        if (url == null && showError) {
            serverLayout.setError(context.getString(raw.isEmpty()
                    ? R.string.home_car_server_required : R.string.home_car_server_invalid));
            serverInput.requestFocus();
        }
        return url;
    }

    private void inspectCertificate() {
        if (!active()) return;
        pendingInspection = null;
        String url = serverUrl(false);
        ClientCertificateOrigin origin = url == null ? null : ClientCertificateOrigin.fromUrl(url);
        int generation = ++requestGeneration;
        certificateAlias.setVisibility(View.GONE);
        certificateExpiry.setVisibility(View.GONE);
        forgetCertificate.setVisibility(View.GONE);
        chooseCertificate.setEnabled(origin != null && !choosing);
        chooseCertificate.setText(R.string.home_car_certificate_choose);
        if (origin == null) {
            certificateStatus.setText(url == null ? R.string.home_car_certificate_enter_server
                    : R.string.home_car_certificate_https_only);
            certificateStatus.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
            certificateOrigin.setVisibility(View.GONE);
            certificateDescription.setText(url == null ? R.string.home_car_certificate_optional
                    : R.string.home_car_certificate_http_notice);
            return;
        }
        certificateOrigin.setText(context.getString(R.string.home_car_certificate_origin, origin.asUrl()));
        certificateOrigin.setVisibility(View.VISIBLE);
        certificateStatus.setText(R.string.home_car_certificate_checking);
        ClientCertificateManager.inspect(activity.getApplicationContext(), url, result -> {
            if (!active() || generation != requestGeneration || !Objects.equals(url, serverUrl(false))) return;
            renderCertificate(result);
        });
    }

    private void renderCertificate(ClientCertificateManager.Result result) {
        boolean selected = result.alias != null && !result.alias.isEmpty();
        certificateAlias.setText(result.alias);
        certificateAlias.setVisibility(selected ? View.VISIBLE : View.GONE);
        certificateExpiry.setVisibility(result.expiresAtMillis > 0 ? View.VISIBLE : View.GONE);
        if (result.expiresAtMillis > 0) certificateExpiry.setText(context.getString(
                R.string.home_car_certificate_expires,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(result.expiresAtMillis))));
        forgetCertificate.setVisibility(selected ? View.VISIBLE : View.GONE);
        forgetCertificate.setEnabled(!choosing);
        chooseCertificate.setText(selected ? R.string.home_car_certificate_change
                : R.string.home_car_certificate_choose);
        chooseCertificate.setEnabled(!choosing);
        certificateStatus.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        switch (result.state) {
            case READY:
                boolean expiresSoon = result.expiresAtMillis > 0
                        && result.expiresAtMillis - System.currentTimeMillis() <= 30L * 24 * 60 * 60 * 1000;
                certificateStatus.setText(expiresSoon ? R.string.home_car_certificate_expires_soon
                        : R.string.home_car_certificate_ready);
                certificateStatus.setTextColor(color(androidx.appcompat.R.attr.colorPrimary));
                certificateDescription.setText(expiresSoon ? R.string.home_car_certificate_renew_description
                        : R.string.home_car_certificate_ready_description);
                break;
            case EXPIRED:
                certificateStatus.setText(R.string.home_car_certificate_expired);
                certificateStatus.setTextColor(color(androidx.appcompat.R.attr.colorError));
                certificateDescription.setText(R.string.home_car_certificate_expired_description);
                break;
            case UNAVAILABLE:
                certificateStatus.setText(R.string.home_car_certificate_unavailable);
                certificateStatus.setTextColor(color(androidx.appcompat.R.attr.colorError));
                certificateDescription.setText(R.string.home_car_certificate_unavailable_description);
                break;
            default:
                certificateStatus.setText(R.string.home_car_certificate_none);
                certificateDescription.setText(R.string.home_car_certificate_optional);
                break;
        }
    }

    private void chooseCertificate() {
        String url = serverUrl(true);
        if (choosing || !active() || url == null || ClientCertificateOrigin.fromUrl(url) == null) return;
        choosing = true;
        chooseCertificate.setEnabled(false);
        forgetCertificate.setEnabled(false);
        serverInput.setEnabled(false);
        saveServer.setEnabled(false);
        reloadDashboard.setEnabled(false);
        requestGeneration++;
        ClientCertificateManager.choose(activity, url, result -> {
            choosing = false;
            if (!active()) return;
            serverInput.setEnabled(true);
            saveServer.setEnabled(true);
            reloadDashboard.setEnabled(true);
            forgetCertificate.setEnabled(true);
            if (result.state == ClientCertificateManager.State.CANCELLED) {
                showNotice(R.string.home_car_certificate_cancelled);
                inspectCertificate();
            } else if (result.state == ClientCertificateManager.State.READY) {
                renderCertificate(result);
                showNotice(R.string.home_car_certificate_selection_saved);
            } else {
                // A rejected candidate never replaces the persisted selection. Re-read that choice
                // instead of displaying the attempted alias as the identity that will be used.
                showNotice(result.state == ClientCertificateManager.State.EXPIRED
                        ? R.string.home_car_certificate_rejected_expired
                        : R.string.home_car_certificate_rejected_unavailable);
                inspectCertificate();
            }
        });
    }

    private void forgetCertificate() {
        String url = serverUrl(true);
        if (url == null || choosing || !active()) return;
        if (forgetConfirmation != null && forgetConfirmation.isShowing()) return;
        forgetConfirmation = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.home_car_certificate_forget_title)
                .setMessage(R.string.home_car_certificate_forget_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_car_certificate_forget, (ignored, which) -> {
                    if (active() && Objects.equals(url, serverUrl(false))) forgetSelection(url);
                }).create();
        forgetConfirmation.setOnDismissListener(ignored -> forgetConfirmation = null);
        forgetConfirmation.show();
    }

    private void forgetSelection(String url) {
        int generation = ++requestGeneration;
        forgetCertificate.setEnabled(false);
        ClientCertificateManager.forget(activity.getApplicationContext(), url, () -> {
            if (!active() || generation != requestGeneration) return;
            forgetCertificate.setEnabled(true);
            showNotice(R.string.home_car_certificate_forgotten);
            inspectCertificate();
        });
    }

    private void saveAndOpen() {
        String url = serverUrl(true);
        if (!active() || choosing || opening || url == null) return;
        opening = true;
        saveServer.setEnabled(false);
        reloadDashboard.setEnabled(false);
        ClientCertificateManager.clearCachedChoices(() -> {
            if (!active()) return;
            openServer.accept(url);
            dismiss();
        });
    }

    private void reload() {
        if (!active() || choosing || opening) return;
        opening = true;
        reloadDashboard.setEnabled(false);
        saveServer.setEnabled(false);
        ClientCertificateManager.clearCachedChoices(() -> {
            if (!active()) return;
            reload.run();
            dismiss();
        });
    }

    private void showNotice(int message) {
        notice.setText(message);
        notice.setVisibility(View.VISIBLE);
    }

    private int color(int attribute) {
        return MaterialColors.getColor(content, attribute);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Nullable private static String displayOrigin(@Nullable String url) {
        if (url == null) return null;
        String normalized = ClientCertificateOrigin.normalizeServerUrl(url);
        if (normalized == null) return null;
        URI uri = URI.create(normalized);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
