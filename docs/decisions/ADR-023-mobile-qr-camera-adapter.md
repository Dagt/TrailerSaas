# ADR-023 — Mobile QR camera adapter

Status: Proposed in Issue #44 / pending PR review.

## Context

ADR-005 selects Flutter. ADR-018 defines the opaque Trailer QR identity, and Driver Hook must identify the physical label before the server authorizes coupling. The existing injectable QrScanner boundary has no native camera implementation.

## Decision

Use mobile_scanner 7.4.2 behind QrScanner for foreground camera-only scanning. Request camera permission, retain no frames, offer no gallery or manually entered Trailer-ID bypass, and release the camera when the route closes. Accept a label token or HTTPS /t/{token} payload, extract only the token, and resolve it using the configured backend; never fetch the scanned host.

The server-derived scan is identification only. Hook still checks active membership, own Driver, current assignment, exact Trailer expectation/substitution, physical eligibility and version. QR response version is an additive projection field required for the existing Hook contract.

## Consequences

One pinned dependency replaces a custom native camera/decoder implementation. Tests inject QrScanner and require no Firebase/camera network. Android/iOS permission and real-label smoke tests remain device validation, not simulated physical operations. No continuous location, photo upload, media queue or offline replay worker is introduced.
