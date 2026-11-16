package me.aap.fermata.addon.web;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.net.Uri;
import android.webkit.WebResourceRequest;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.security.ClientCertificateManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebClient extends WebViewClientCompat {
	BooleanConsumer loading;

	@Override
	public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
		if (!(view instanceof FermataWebView browser)) {
			request.ignore();
			return;
		}
		WebBrowserAddon addon = browser.getAddon();
		if (addon == null) {
			request.ignore();
			return;
		}
		Context ctx = view.getContext();
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(ctx).peek();
		Activity phoneActivity = a != null && !a.isCarActivityNotMirror() &&
				a.getAppActivity() instanceof Activity activity ? activity : null;
		ClientCertificateManager.respond(view, addon.getHomeUrl(), request, phoneActivity);
	}

	@Override
	public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
		// Client authentication never weakens verification of the server's identity.
		handler.cancel();
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (loading != null) {
			loading.accept(true);
		} else {
			MainActivityDelegate.getActivityDelegate(view.getContext())
					.onSuccess(a -> a.setContentLoading(new Promise<>()));
		}
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		FermataWebView v = (FermataWebView) view;
		FutureSupplier<MainActivityDelegate> f =
				MainActivityDelegate.getActivityDelegate(v.getContext());
		f.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));

		if (loading != null) {
			loading.accept(false);
			loading = null;
		}

		super.onPageFinished(view, url);
		v.hideKeyboard();
		v.pageLoaded(url);
		f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
											@NonNull WebResourceRequest request) {
		Uri target = request.getUrl();
		String scheme = target.getScheme();

		if ((scheme == null)
				|| (!"http".equalsIgnoreCase(scheme)
				&& !"https".equalsIgnoreCase(scheme))) {
			Log.w("Blocked WebView navigation with an unsupported scheme");
			return true;
		}

		if (isYoutubeUri(target)) {
			try {
				MainActivityDelegate a =
						MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;

				if (!(a.showFragment(me.aap.fermata.R.id.youtube_fragment)
						instanceof YoutubeFragment f)) {
					return false;
				}

				f.loadUrl(target.toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		return ((host != null)
				&& ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com"))
				|| host.equals("youtu.be")));
	}

	@Override
	public void onReceivedError(@NonNull WebView view,
								@NonNull WebResourceRequest request,
								@NonNull WebResourceErrorCompat error) {
		if (WebViewFeature.isFeatureSupported(
				WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) {
			Log.e("Web error received: " + error.getErrorCode());
		} else {
			Log.e("Web error received");
		}

		super.onReceivedError(view, request, error);
	}
}
