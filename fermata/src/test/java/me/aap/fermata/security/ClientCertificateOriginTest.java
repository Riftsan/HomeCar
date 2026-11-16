package me.aap.fermata.security;

import static org.junit.Assert.*;

import org.junit.Test;

public class ClientCertificateOriginTest {
	@Test public void defaultHttpsPortAndHostCaseAreEquivalent() {
		ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl("https://HOME.EXAMPLE.COM/dashboard");
		assertEquals(ClientCertificateOrigin.fromUrl("https://home.example.com:443/other"), origin);
		assertTrue(origin.matches("HOME.EXAMPLE.COM", 443));
		assertEquals("https://home.example.com:443", origin.asUrl());
	}

	@Test public void grantCannotFollowRedirectToOtherAuthority() {
		ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl("https://home.example.com");
		assertFalse(origin.matches("home.example.com.attacker.test", 443));
		assertFalse(origin.matches("other.home.example.com", 443));
		assertFalse(origin.matches("example.com", 443));
		assertFalse(origin.matches("home.example.com.", 443));
		assertFalse(origin.matches("home.example.com", 8443));
		assertFalse(origin.matches(null, 443));
	}

	@Test public void credentialsAndAmbiguousAuthoritiesAreRejected() {
		for (String input : new String[]{"https://user:password@home.example.com", "https://@home.example.com",
				"https://home.example.com:", "https://home.example.com:0", "https://home.example.com:65536",
				"https://home.example.com:-1", "https://home.example.com:abc", "https:///dashboard",
				"https://home.example.com\\@attacker.test", "https://home.example.com\n.attacker.test"}) {
			assertNull(input, ClientCertificateOrigin.normalizeServerUrl(input));
			assertNull(input, ClientCertificateOrigin.fromUrl(input));
		}
	}

	@Test public void certificateGrantRequiresHttps() {
		assertNull(ClientCertificateOrigin.fromUrl("http://home.example.com:443"));
		assertNull(ClientCertificateOrigin.fromUrl("file:///home.example.com"));
		assertNull(ClientCertificateOrigin.fromUrl("javascript:alert(1)"));
		assertNull(ClientCertificateOrigin.fromUrl(null));
		assertNull(ClientCertificateOrigin.fromUrl(" "));
	}

	@Test public void localHttpStillWorksWithoutCertificateGrant() {
		assertEquals("http://192.0.2.10:8123/dashboard", ClientCertificateOrigin.normalizeServerUrl(
				" http://192.0.2.10:8123/dashboard "));
		assertNull(ClientCertificateOrigin.fromUrl("http://192.0.2.10:8123/dashboard"));
	}

	@Test public void urlNormalizationPreservesEncodedDashboardAndQuery() {
		assertEquals("https://home.example.com/lovelace/a%2Fb?q=a%20b#room", ClientCertificateOrigin.normalizeServerUrl(
				"HTTPS://HOME.EXAMPLE.COM/lovelace/a%2Fb?q=a%20b#room"));
		assertEquals("https://home.example.com/", ClientCertificateOrigin.normalizeServerUrl("https://HOME.EXAMPLE.COM"));
	}

	@Test public void storageScopeExcludesPrivatePathsAndTokens() {
		ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl("https://home.example.com/dashboard?token=secret#room");
		assertEquals("https://home.example.com:443", origin.preferenceKey());
	}

	@Test public void ipv6AuthorityRoundTripsWithoutLosingPort() {
		ClientCertificateOrigin origin = ClientCertificateOrigin.fromUrl("https://[2001:db8::1]:8443/");
		assertTrue(origin.matches("2001:db8::1", 8443));
		assertTrue(origin.matches("[2001:DB8::1]", 8443));
		assertEquals("https://[2001:db8::1]:8443", origin.asUrl());
		assertEquals("https://[2001:db8::1]:8443/", ClientCertificateOrigin.normalizeServerUrl(origin.asUrl()));
	}
}
