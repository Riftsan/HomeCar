package me.aap.fermata.security;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.junit.Test;

public class CertificateConstraintsTest {
	private final TestCertificate leaf = new TestCertificate();

	@Test public void validClientPurposeAcceptedWithMatchingKeyType() throws Exception {
		assertTrue(CertificateConstraints.accepts(chain(), new String[]{"RSA"}, null));
		assertFalse(CertificateConstraints.accepts(chain(), new String[]{"EC"}, null));
	}

	@Test public void serverOnlyIdentityIsRejected() throws Exception {
		leaf.usage = List.of("1.3.6.1.5.5.7.3.1");
		assertFalse(CertificateConstraints.accepts(chain(), null, null));
	}

	@Test public void unrestrictedAndAnyPurposeCertificatesAreAccepted() throws Exception {
		leaf.usage = null;
		assertTrue(CertificateConstraints.accepts(chain(), null, null));
		leaf.usage = List.of("2.5.29.37.0");
		assertTrue(CertificateConstraints.accepts(chain(), null, null));
	}

	@Test public void signingPermissionRequiredWhenKeyUsageIsPresent() throws Exception {
		leaf.keyUsage = new boolean[]{false, false, true};
		assertFalse(CertificateConstraints.accepts(chain(), null, null));
		leaf.keyUsage = new boolean[0];
		assertFalse(CertificateConstraints.accepts(chain(), null, null));
		leaf.keyUsage = null;
		assertTrue(CertificateConstraints.accepts(chain(), null, null));
	}

	@Test public void requestedIssuerMayMatchIntermediateOrRoot() throws Exception {
		TestCertificate intermediate = new TestCertificate();
		intermediate.issuer = new X500Principal("CN=Root");
		assertTrue(CertificateConstraints.accepts(new X509Certificate[]{leaf, intermediate}, null,
				new Principal[]{new X500Principal("CN=Root")}));
		assertFalse(CertificateConstraints.accepts(chain(), null,
				new Principal[]{new X500Principal("CN=Unrelated")}));
	}

	@Test public void missingCertificateMaterialIsRejected() throws Exception {
		assertFalse(CertificateConstraints.accepts(null, null, null));
		assertFalse(CertificateConstraints.accepts(new X509Certificate[0], null, null));
		assertFalse(CertificateConstraints.accepts(new X509Certificate[]{null}, null, null));
	}

	private X509Certificate[] chain() { return new X509Certificate[]{leaf}; }

	/** Metadata fixture only. No private key or production certificate is part of these tests. */
	@SuppressWarnings("deprecation")
	private static class TestCertificate extends X509Certificate {
		List<String> usage = List.of("1.3.6.1.5.5.7.3.2");
		boolean[] keyUsage = {true};
		X500Principal issuer = new X500Principal("CN=Intermediate");
		@Override public List<String> getExtendedKeyUsage() { return usage; }
		@Override public boolean[] getKeyUsage() { return keyUsage; }
		@Override public X500Principal getIssuerX500Principal() { return issuer; }
		@Override public PublicKey getPublicKey() {
			return new PublicKey() {
				public String getAlgorithm() { return "RSA"; }
				public String getFormat() { return "X.509"; }
				public byte[] getEncoded() { return new byte[0]; }
			};
		}
		@Override public void checkValidity() {}
		@Override public void checkValidity(Date date) {}
		@Override public int getVersion() { return 3; }
		@Override public BigInteger getSerialNumber() { return BigInteger.ONE; }
		@Override public Principal getIssuerDN() { return issuer; }
		@Override public Principal getSubjectDN() { return new X500Principal("CN=Test phone"); }
		@Override public Date getNotBefore() { return new Date(0); }
		@Override public Date getNotAfter() { return new Date(Long.MAX_VALUE); }
		@Override public byte[] getTBSCertificate() { return new byte[0]; }
		@Override public byte[] getSignature() { return new byte[0]; }
		@Override public String getSigAlgName() { return "SHA256withRSA"; }
		@Override public String getSigAlgOID() { return "1.2.840.113549.1.1.11"; }
		@Override public byte[] getSigAlgParams() { return null; }
		@Override public boolean[] getIssuerUniqueID() { return null; }
		@Override public boolean[] getSubjectUniqueID() { return null; }
		@Override public int getBasicConstraints() { return -1; }
		@Override public byte[] getEncoded() { return new byte[0]; }
		@Override public void verify(PublicKey key) {}
		@Override public void verify(PublicKey key, String provider) {}
		@Override public String toString() { return "Test client certificate metadata"; }
		@Override public Set<String> getCriticalExtensionOIDs() { return null; }
		@Override public Set<String> getNonCriticalExtensionOIDs() { return null; }
		@Override public byte[] getExtensionValue(String oid) { return null; }
		@Override public boolean hasUnsupportedCriticalExtension() { return false; }
	}
}
