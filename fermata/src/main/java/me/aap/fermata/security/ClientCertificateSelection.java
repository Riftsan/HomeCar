package me.aap.fermata.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Coordinates async picker/key loading so a forgotten or superseded choice cannot return. */
final class ClientCertificateSelection {
	interface Store {
		String read(String origin);
		void write(String origin, String alias);
		void remove(String origin);
	}

	static final class Ticket {
		final String origin;
		final long generation;
		final String alias;
		Ticket(String origin, long generation, String alias) {
			this.origin = origin;
			this.generation = generation;
			this.alias = alias;
		}
	}

	private final Store store;
	private final Map<String, Long> generations = new HashMap<>();

	ClientCertificateSelection(Store store) {
		this.store = store;
	}

	synchronized String get(ClientCertificateOrigin origin) {
		return store.read(origin.preferenceKey());
	}

	synchronized Ticket snapshot(ClientCertificateOrigin origin) {
		String key = origin.preferenceKey();
		return new Ticket(key, generations.getOrDefault(key, 0L), store.read(key));
	}

	synchronized Ticket start(ClientCertificateOrigin origin) {
		advance(origin.preferenceKey());
		return snapshot(origin);
	}

	synchronized boolean isCurrent(Ticket ticket) {
		return ticket.generation == generations.getOrDefault(ticket.origin, 0L);
	}

	synchronized Ticket select(Ticket ticket, String alias) {
		if (!isCurrent(ticket) || alias == null || alias.isEmpty()) return null;
		store.write(ticket.origin, alias);
		advance(ticket.origin);
		return new Ticket(ticket.origin, generations.get(ticket.origin), alias);
	}

	synchronized void cancel(Ticket ticket) {
		if (isCurrent(ticket)) advance(ticket.origin);
	}

	synchronized void forget(ClientCertificateOrigin origin) {
		advance(origin.preferenceKey());
		store.remove(origin.preferenceKey());
	}

	synchronized boolean removeIfCurrent(Ticket ticket, String alias) {
		if (!isCurrent(ticket) || !Objects.equals(alias, store.read(ticket.origin))) return false;
		advance(ticket.origin);
		store.remove(ticket.origin);
		return true;
	}

	private void advance(String origin) {
		generations.put(origin, generations.getOrDefault(origin, 0L) + 1);
	}
}
