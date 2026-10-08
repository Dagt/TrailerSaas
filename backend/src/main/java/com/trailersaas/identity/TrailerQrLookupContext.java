package com.trailersaas.identity;

/** Exact public QR lookup on an existing transaction; confers no Tenant permission. */
public interface TrailerQrLookupContext {
    void establish(String canonicalPublicToken);
}
