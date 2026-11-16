package me.aap.fermata.security;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class ClientCertificateSelectionTest {
	private final ClientCertificateOrigin home = ClientCertificateOrigin.fromUrl("https://home.example.com");
	private final MemoryStore store = new MemoryStore();
	private final ClientCertificateSelection selection = new ClientCertificateSelection(store);

	@Test public void cancelledChangePreservesExistingCertificateAndAllowsRetry() {
		select(home, "original");
		ClientCertificateSelection.Ticket picker = selection.start(home);
		selection.cancel(picker);
		assertEquals("original", selection.get(home));
		assertNull(selection.select(picker, "late-result"));
		select(home, "replacement");
		assertEquals("replacement", selection.get(home));
	}

	@Test public void cancelledFirstChoiceDoesNotCreatePermanentDenial() {
		selection.cancel(selection.start(home));
		assertNull(selection.get(home));
		select(home, "retried");
		assertEquals("retried", selection.get(home));
	}

	@Test public void forgetInvalidatesPendingPickerAndKeyLoad() {
		select(home, "original");
		ClientCertificateSelection.Ticket load = selection.snapshot(home);
		ClientCertificateSelection.Ticket picker = selection.start(home);
		selection.forget(home);
		assertFalse(selection.isCurrent(load));
		assertNull(selection.select(picker, "late-result"));
		assertNull(selection.get(home));
	}

	@Test public void olderPickerCannotOverwriteNewerUserChoice() {
		ClientCertificateSelection.Ticket older = selection.start(home);
		select(home, "newer");
		assertNull(selection.select(older, "older"));
		selection.cancel(older);
		assertEquals("newer", selection.get(home));
	}

	@Test public void staleGrantFailureCannotDeleteNewSelectionEvenWhenAliasIsReused() {
		select(home, "same-alias");
		ClientCertificateSelection.Ticket oldLoad = selection.snapshot(home);
		selection.forget(home);
		select(home, "same-alias");
		assertFalse(selection.removeIfCurrent(oldLoad, "same-alias"));
		assertFalse(selection.isCurrent(oldLoad));
		assertEquals("same-alias", selection.get(home));
	}

	@Test public void selectedResultIsInvalidAfterForgetBeforeCacheClearCompletes() {
		ClientCertificateSelection.Ticket selected = selection.select(selection.start(home), "same-alias");
		assertNotNull(selected);
		selection.forget(home);
		select(home, "same-alias");
		assertFalse(selection.isCurrent(selected));
	}

	@Test public void missingGrantRemovesOnlyMatchingCurrentAlias() {
		select(home, "removed-on-device");
		ClientCertificateSelection.Ticket load = selection.snapshot(home);
		assertFalse(selection.removeIfCurrent(load, "some-other-alias"));
		assertTrue(selection.removeIfCurrent(load, "removed-on-device"));
		assertNull(selection.get(home));
	}

	@Test public void hostsAndPortsHaveIndependentGrants() {
		ClientCertificateOrigin otherHost = ClientCertificateOrigin.fromUrl("https://other.example.com");
		ClientCertificateOrigin otherPort = ClientCertificateOrigin.fromUrl("https://home.example.com:8443");
		select(home, "home");
		select(otherHost, "other-host");
		select(otherPort, "other-port");
		ClientCertificateSelection.Ticket load = selection.snapshot(otherPort);
		selection.forget(home);
		assertEquals("other-host", selection.get(otherHost));
		assertEquals("other-port", selection.get(otherPort));
		assertTrue(selection.isCurrent(load));
	}

	@Test public void emptyPickerResultCannotReplaceSavedChoice() {
		select(home, "original");
		ClientCertificateSelection.Ticket picker = selection.start(home);
		assertNull(selection.select(picker, null));
		assertNull(selection.select(picker, ""));
		assertEquals("original", selection.get(home));
	}

	private void select(ClientCertificateOrigin origin, String alias) {
		assertNotNull(selection.select(selection.start(origin), alias));
	}

	private static class MemoryStore implements ClientCertificateSelection.Store {
		final Map<String, String> values = new HashMap<>();
		public String read(String origin) { return values.get(origin); }
		public void write(String origin, String alias) { values.put(origin, alias); }
		public void remove(String origin) { values.remove(origin); }
	}
}
