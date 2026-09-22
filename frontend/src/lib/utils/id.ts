/**
 * A random identifier that works outside a secure context.
 *
 * <p>{@code crypto.randomUUID()} is only defined on HTTPS origins and on localhost. Open the
 * dev server from a phone on the LAN - {@code http://192.168.1.3:3000} - and it is simply
 * undefined, so anything that called it threw a TypeError. That took out sending a message
 * and showing a toast, which is most of the app.
 *
 * <p>{@code crypto.getRandomValues()} carries no such restriction, so the fallback builds a
 * proper RFC 4122 version 4 UUID from it. The final branch is there for ancient or locked
 * down environments; the values are weaker but these ids only need to be unique, not
 * unguessable - they key idempotency and React lists, never anything security bearing.
 */
export function randomId(): string {
  const cryptoApi = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined;

  if (typeof cryptoApi?.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }

  if (typeof cryptoApi?.getRandomValues === 'function') {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
    // Version 4, variant 10xx - the two fields that make it a valid v4 UUID.
    bytes[6] = (bytes[6]! & 0x0f) | 0x40;
    bytes[8] = (bytes[8]! & 0x3f) | 0x80;

    const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  }

  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}-${Math.random()
    .toString(16)
    .slice(2, 10)}`;
}
