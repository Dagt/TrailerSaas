package com.trailersaas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded keyset position, bound to its read context; never an authorization credential. */
public final class ReadCursor {
  private ReadCursor() {}
  private static String digest(String context) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(context.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  public static String encode(String context, UUID... ids) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(("1|"+digest(context)+"|"+String.join("|",Arrays.stream(ids).map(UUID::toString).toList())).getBytes(StandardCharsets.UTF_8));
  }
  public static List<UUID> decode(String value, String context, int count) {
    if(value==null) return List.of();
    if(value.length()>512 || !value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
    var parts=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8).split("\\|",-1);
    if(parts.length!=count+2 || !parts[0].equals("1") || !parts[1].equals(digest(context))) throw new IllegalArgumentException();
    var ids=Arrays.stream(parts).skip(2).map(UUID::fromString).toList();
    if(!encode(context,ids.toArray(UUID[]::new)).equals(value)) throw new IllegalArgumentException();
    return ids;
  }
}
