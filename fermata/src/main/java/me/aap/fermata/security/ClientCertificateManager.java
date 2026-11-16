package me.aap.fermata.security;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.KeyChain;
import android.webkit.ClientCertRequest;
import android.webkit.WebView;

import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uses Android's user-granted KeyChain handles. Private keys are never exported or persisted.
 * The only stored value is an alias for an exact HTTPS host/port. Server TLS verification is
 * left to WebView. Select a certificate on the phone before using the projected car screen.
 */
public final class ClientCertificateManager {
	public enum State { NONE, READY, CANCELLED, UNAVAILABLE, EXPIRED, INVALID_URL }

	public static final class Result {
		public final State state;
		public final String alias;
		public final long expiresAtMillis;
		private Result(State state, String alias, long expiresAtMillis) {
			this.state = state;
			this.alias = alias;
			this.expiresAtMillis = expiresAtMillis;
		}
	}

	@FunctionalInterface
	public interface Callback { void onResult(Result result); }

	private interface MaterialCallback { void onResult(Result result, Material material); }
	private static final String PREFS = "homecar_client_cert_v2";
	private static final Handler MAIN = new Handler(Looper.getMainLooper());
	private static final ExecutorService KEY_IO = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "HomeCar-KeyChain");
		thread.setDaemon(true);
		return thread;
	});
	private static ClientCertificateSelection selection;
	// Accessed only on the main thread. Several resource requests may ask at once.
	private static final Set<ClientCertificateOrigin> CHOOSERS = new HashSet<>();
	private static long requestGeneration;

	private ClientCertificateManager() {}

	/** Explicit phone-side select/change. Cancellation preserves any existing choice. */
	public static void choose(Activity activity, String serverUrl, Callback callback) {
		onMain(() -> {
			ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl(serverUrl);
			if (origin == null) {
				callback.onResult(result(State.INVALID_URL, null));
				return;
			}
			choose(activity, origin, null, null, (result, material) -> callback.onResult(result));
		});
	}

	public static String getSavedAlias(Context context, String serverUrl) {
		ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl(serverUrl);
		return origin == null ? null : selections(context).get(origin);
	}

	/** Checks the current grant, client-auth purpose and expiration off the UI thread. */
	public static void inspect(Context context, String serverUrl, Callback callback) {
		Context app = context.getApplicationContext();
		ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl(serverUrl);
		if (origin == null) {
			onMain(() -> callback.onResult(result(State.INVALID_URL, null)));
			return;
		}
		ClientCertificateSelection selections = selections(app);
		ClientCertificateSelection.Ticket ticket = selections.snapshot(origin);
		String alias = ticket.alias;
		if (alias == null) {
			onMain(() -> callback.onResult(result(State.NONE, null)));
			return;
		}
		load(app, alias, null, null, (result, material) -> {
			if (!selections.isCurrent(ticket)) {
				callback.onResult(result(State.CANCELLED, null));
				return;
			}
			if (result.state != State.READY && selections.removeIfCurrent(ticket, alias)) {
				clearWebViewCachedChoices(() -> callback.onResult(result));
			} else callback.onResult(result);
		});
	}

	/** Forgets HomeCar's choice only; it does not delete the Android-installed certificate. */
	public static void forget(Context context, String serverUrl, Runnable completion) {
		onMain(() -> {
			ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl(serverUrl);
			if (origin != null) selections(context).forget(origin);
			clearCachedChoices(completion);
		});
	}

	/**
	 * Call before loading a changed server URL or after a user requests certificate retry.
	 * Pending TLS requests are invalidated so an old page cannot receive a late picker result.
	 */
	public static void clearCachedChoices(Runnable completion) {
		onMain(() -> {
			requestGeneration++;
			clearWebViewCachedChoices(completion);
		});
	}

	private static void clearWebViewCachedChoices(Runnable completion) {
		WebView.clearClientCertPreferences(completion == null ? () -> {} : completion);
	}

	/** A null phoneActivity means projection: use a prior phone grant without launching UI. */
	public static void respond(WebView view, String configuredUrl, ClientCertRequest request,
			Activity phoneActivity) {
		onMain(() -> {
			RequestReply reply = new RequestReply(request);
			ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl(configuredUrl);
			if (origin == null || !origin.matches(request.getHost(), request.getPort())) {
				reply.ignore();
				return;
			}
			Context app = view.getContext().getApplicationContext();
			ClientCertificateSelection selections = selections(app);
			ClientCertificateSelection.Ticket ticket = selections.snapshot(origin);
			String alias = ticket.alias;
			if (alias == null) {
				chooseForRequest(phoneActivity, origin, request, reply);
				return;
			}
			load(app, alias, request.getKeyTypes(), request.getPrincipals(), (result, material) -> {
				if (!reply.isCurrent() || !selections.isCurrent(ticket)) reply.ignore();
				else if (material != null) reply.proceed(material);
				else {
					if (!selections.removeIfCurrent(ticket, alias)) {
						reply.ignore();
						return;
					}
					ClientCertificateSelection.Ticket removed = selections.snapshot(origin);
					// A removed certificate or revoked app grant can be selected again on the phone.
					clearWebViewCachedChoices(() -> {
						if (selections.isCurrent(removed)) chooseForRequest(phoneActivity, origin, request, reply);
						else reply.ignore();
					});
				}
			});
		});
	}

	private static void chooseForRequest(Activity activity, ClientCertificateOrigin origin,
			ClientCertRequest request, RequestReply reply) {
		if (!reply.isCurrent() || activity == null || activity.isFinishing() || activity.isDestroyed()) {
			reply.ignore();
			return;
		}
		choose(activity, origin, request.getKeyTypes(), request.getPrincipals(), (result, material) -> {
			if (material == null) reply.ignore();
			else reply.proceed(material);
		});
	}

	private static void choose(Activity activity, ClientCertificateOrigin origin,
			String[] keyTypes, Principal[] issuers, MaterialCallback callback) {
		if (activity.isFinishing() || activity.isDestroyed()) {
			callback.onResult(result(State.UNAVAILABLE, null), null);
			return;
		}
		Context app = activity.getApplicationContext();
		ClientCertificateSelection selections = selections(app);
		if (!CHOOSERS.add(origin)) {
			callback.onResult(result(State.CANCELLED, selections.get(origin)), null);
			return;
		}
		ClientCertificateSelection.Ticket ticket = selections.start(origin);
		String oldAlias = ticket.alias;
		try {
			KeyChain.choosePrivateKeyAlias(activity, alias -> onMain(() -> {
				if (alias == null) {
					CHOOSERS.remove(origin);
					selections.cancel(ticket);
					callback.onResult(result(State.CANCELLED, oldAlias), null);
					return;
				}
				load(app, alias, keyTypes, issuers, (result, material) -> {
					CHOOSERS.remove(origin);
					if (!selections.isCurrent(ticket)) {
						callback.onResult(result(State.CANCELLED, null), null);
					} else if (material == null) {
						selections.cancel(ticket);
						callback.onResult(result, null);
					} else {
						ClientCertificateSelection.Ticket selected = selections.select(ticket, alias);
						if (selected == null) callback.onResult(result(State.CANCELLED, null), null);
						else clearWebViewCachedChoices(() -> {
							if (selections.isCurrent(selected)) callback.onResult(result, material);
							else callback.onResult(result(State.CANCELLED, null), null);
						});
					}
				});
			}), keyTypes, issuers, Uri.parse(origin.asUrl()), oldAlias);
		} catch (RuntimeException ex) {
			CHOOSERS.remove(origin);
			selections.cancel(ticket);
			callback.onResult(result(State.UNAVAILABLE, oldAlias), null);
		}
	}

	private static void load(Context app, String alias, String[] keyTypes, Principal[] issuers,
			MaterialCallback callback) {
		KEY_IO.execute(() -> {
			Material material = null;
			State state = State.UNAVAILABLE;
			try {
				PrivateKey key = KeyChain.getPrivateKey(app, alias);
				X509Certificate[] chain = KeyChain.getCertificateChain(app, alias);
				if (key != null && chain != null && chain.length != 0 &&
						CertificateConstraints.accepts(chain, keyTypes, issuers)) {
					chain[0].checkValidity();
					material = new Material(key, chain);
					state = State.READY;
				}
			} catch (CertificateExpiredException | CertificateNotYetValidException ex) {
				state = State.EXPIRED;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			} catch (Exception ex) {
				// Alias, certificate identity and provider error details are never logged.
			}
			Material resolved = material;
			Result result = new Result(state, alias,
					material == null ? 0 : material.chain[0].getNotAfter().getTime());
			onMain(() -> callback.onResult(result, resolved));
		});
	}

	private static synchronized ClientCertificateSelection selections(Context context) {
		if (selection == null) {
			SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			selection = new ClientCertificateSelection(new ClientCertificateSelection.Store() {
				public String read(String origin) { return prefs.getString(origin, null); }
				public void write(String origin, String alias) { prefs.edit().putString(origin, alias).apply(); }
				public void remove(String origin) { prefs.edit().remove(origin).apply(); }
			});
		}
		return selection;
	}

	private static Result result(State state, String alias) { return new Result(state, alias, 0); }
	private static void onMain(Runnable task) {
		if (Looper.myLooper() == Looper.getMainLooper()) task.run();
		else MAIN.post(task);
	}

	private static final class Material {
		final PrivateKey key;
		final X509Certificate[] chain;
		Material(PrivateKey key, X509Certificate[] chain) { this.key = key; this.chain = chain; }
	}

	private static final class RequestReply {
		private final ClientCertRequest request;
		private final AtomicBoolean completed = new AtomicBoolean();
		private final long generation = requestGeneration;
		RequestReply(ClientCertRequest request) { this.request = request; }
		boolean isCurrent() { return generation == requestGeneration && !completed.get(); }
		void ignore() { if (completed.compareAndSet(false, true)) request.ignore(); }
		void proceed(Material material) {
			if (!isCurrent()) {
				ignore();
				return;
			}
			if (completed.compareAndSet(false, true)) request.proceed(material.key, material.chain);
		}
	}
}
