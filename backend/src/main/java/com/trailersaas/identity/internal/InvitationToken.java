package com.trailersaas.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
final class InvitationToken {
    private final SecureRandom random = new SecureRandom();
    String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    String validatedHash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) { throw InvitationFailure.invalid(); }
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) {
            throw InvitationFailure.invalid();
        }
        return hash(token);
    }
    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required JDK digest is unavailable.");
        }
    }
}
