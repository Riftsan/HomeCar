package me.aap.fermata.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** The exact HTTPS authority to which a user's certificate choice is granted. */
public final class ClientCertificateOrigin {
	public final String host;
	public final int port;

	private ClientCertificateOrigin(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public static ClientCertificateOrigin fromUrl(String value) {
		URI uri = parseServerUrl(value);
		if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return null;
		return new ClientCertificateOrigin(normalizeHost(uri.getHost()),
				uri.getPort() == -1 ? 443 : uri.getPort());
	}

	/** Returns null for malformed URLs, userinfo, unsupported schemes or invalid ports. */
	public static String normalizeServerUrl(String value) {
		URI uri = parseServerUrl(value);
		if (uri == null) return null;
		String host = normalizeHost(uri.getHost());
		StringBuilder result = new StringBuilder(uri.getScheme().toLowerCase(Locale.ROOT))
				.append("://").append(authorityHost(host));
		if (uri.getPort() != -1) result.append(':').append(uri.getPort());
		String path = uri.getRawPath();
		result.append(path == null || path.isEmpty() ? "/" : path);
		if (uri.getRawQuery() != null) result.append('?').append(uri.getRawQuery());
		if (uri.getRawFragment() != null) result.append('#').append(uri.getRawFragment());
		try {
			return new URI(result.toString()).toASCIIString();
		} catch (URISyntaxException ex) {
			return null;
		}
	}

	public boolean matches(String requestHost, int requestPort) {
		return requestPort == port && host.equals(normalizeHost(requestHost));
	}

	public String asUrl() {
		return "https://" + authorityHost(host) + ':' + port;
	}

	/** Contains neither URL paths, query tokens nor credentials. */
	String preferenceKey() {
		return asUrl();
	}

	private static URI parseServerUrl(String value) {
		if (value == null || value.trim().isEmpty()) return null;
		try {
			URI uri = new URI(value.trim());
			String scheme = uri.getScheme();
			if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) ||
					uri.isOpaque() || uri.getRawUserInfo() != null || uri.getHost() == null ||
					uri.getHost().isEmpty() || uri.getPort() == 0 || uri.getPort() < -1 ||
					uri.getPort() > 65535) return null;
			// URI accepts an empty port separator; reject it rather than silently changing scope.
			if (uri.getRawAuthority().endsWith(":")) return null;
			return uri;
		} catch (URISyntaxException | IllegalArgumentException ex) {
			return null;
		}
	}

	private static String normalizeHost(String value) {
		if (value == null) return null;
		if (value.startsWith("[") && value.endsWith("]")) {
			value = value.substring(1, value.length() - 1);
		}
		return value.toLowerCase(Locale.ROOT);
	}

	private static String authorityHost(String host) {
		return host.indexOf(':') >= 0 ? '[' + host + ']' : host;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ClientCertificateOrigin origin &&
				port == origin.port && host.equals(origin.host);
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, port);
	}
}
