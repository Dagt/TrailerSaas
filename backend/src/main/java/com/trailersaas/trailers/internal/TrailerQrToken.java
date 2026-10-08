package com.trailersaas.trailers.internal;
import java.security.SecureRandom;
import java.util.Base64;
final class TrailerQrToken {
    private static final SecureRandom RANDOM=new SecureRandom();
    private TrailerQrToken() {}
    static String generate() {
        byte[] bytes=new byte[16]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String requireCanonical(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{22}")) { throw TrailerFailure.invalid(); }
        var bytes=Base64.getUrlDecoder().decode(token);
        if(bytes.length!=16 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) { throw TrailerFailure.invalid(); }
        return token;
    }
}
