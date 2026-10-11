package com.trailersaas.documents.internal;

import java.time.Duration;
import java.time.Instant;

/** Only server-owned keys; no arbitrary client keys or public URL fallback. */
interface ObjectStoragePort extends AutoCloseable {
  record Signed(String url,Instant expiresAt) {}
  record Verified(long size,String contentType,String sha256) {}
  Signed put(String key,String contentType,long size,Duration lifetime);
  Verified copyAndVerify(String temporaryKey,String publicationKey,long expectedSize);
  Signed get(String key,Duration lifetime);
  boolean exists(String key);
  default void close() {}
}
