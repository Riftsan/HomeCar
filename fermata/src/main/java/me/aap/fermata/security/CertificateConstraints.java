package me.aap.fermata.security;

import java.security.Principal;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;

/** Only use a saved identity when it can satisfy this server's client-auth request. */
final class CertificateConstraints {
	private CertificateConstraints() {}

	static boolean accepts(X509Certificate[] chain, String[] keyTypes, Principal[] issuers)
			throws CertificateParsingException {
		if (chain == null || chain.length == 0 || chain[0] == null) return false;
		X509Certificate leaf = chain[0];
		boolean[] usage = leaf.getKeyUsage();
		if (usage != null && (usage.length == 0 || !usage[0])) return false;
		List<String> extended = leaf.getExtendedKeyUsage();
		if (extended != null && !extended.contains("1.3.6.1.5.5.7.3.2") &&
				!extended.contains("2.5.29.37.0")) return false;
		if (keyTypes != null && keyTypes.length != 0) {
			boolean matches = false;
			for (String type : keyTypes) {
				if (leaf.getPublicKey().getAlgorithm().equalsIgnoreCase(type)) matches = true;
			}
			if (!matches) return false;
		}
		if (issuers != null && issuers.length != 0) {
			for (X509Certificate certificate : chain) {
				if (certificate == null) return false;
				for (Principal issuer : issuers) {
					if (certificate.getIssuerX500Principal().equals(issuer)) return true;
				}
			}
			return false;
		}
		return true;
	}
}
