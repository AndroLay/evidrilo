# Security policy

Evidrilo is a local-first Kotlin Multiplatform application with an optional
ASP.NET Core and PostgreSQL platform lane. The free learning flow must remain
usable without an account, backend, network, AI provider, or billing key.

## Reporting a vulnerability

Do not disclose credentials, private user data, provider payloads, or an
exploitable reproduction in a public issue. Use the repository's private
security-advisory channel when it is available:

<https://github.com/AndroLay/evidrilo/security/advisories/new>

If private reporting is unavailable, open a minimal issue that contains no
secret or exploit detail and request a private contact channel. Include the
affected boundary, a safe reproduction summary, impact, and the first version
where the issue was observed.

## Security boundaries

- Never commit API keys, passwords, refresh tokens, signing material, service
  role keys, provider payloads, or personal data.
- Keep account credentials in the managed identity provider; the application
  API must not persist passwords.
- Treat RevenueCat entitlement state as provider-controlled and fail closed for
  unknown products or entitlements. Evidrilo accepts only monthly/yearly
  products for `evidrilo_pro`.
- Keep local drafts and history separate from optional authenticated sync.
- Validate authorization, input size, idempotency, deletion lifecycle, and
  audit boundaries on the server before enabling a managed deployment.

The security policy does not claim that production, provider, device, or human
security validation has been completed. Those gates remain environment-specific
and are recorded as such in the release documentation.
