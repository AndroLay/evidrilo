# Apple sign-in setup

The codebase contains an Apple OAuth path through Supabase Auth, but Apple
sign-in is intentionally deferred and disabled for the current release. When
enabled in the future, the client requests the `email` and `name` scopes, uses
PKCE plus a per-request state value, and accepts the callback only for the
registered `evidrilo://auth/callback` URL. Provider identity and the existing
Supabase user ID are checked before a link session is stored. No Apple signing
key or client secret belongs in the app.

## Current decision — 30 September 2026

The owner does not currently have Apple Developer setup for Evidrilo and has
chosen not to activate **Continue with Apple**. Keep the Apple build flag off
for every target and leave the Apple provider disabled in Supabase Auth. Apple
client IDs, signing keys, and generated secrets may remain unset; an empty
Apple provider configuration is intentional and does not block the app's API,
email sign-in, or Google sign-in when Google is configured.

Do not add placeholder Apple credentials or claim that Apple sign-in is
available. The configuration steps below are a future reactivation runbook,
not a current staging or release requirement. See [D-132](../decisions.md#d-132-defer-apple-sign-in-and-keep-apple-provider-configuration-empty).

## Supabase and Apple configuration

Use these steps only if the owner later chooses to activate Apple sign-in and
has the required Apple Developer setup:

1. In Apple Developer, enable Sign in with Apple for the app's App ID and create
   a Services ID for Supabase's browser OAuth flow.
2. Configure the Services ID's domain and return URL using the values shown by
   the Supabase Apple provider setup. The Apple return URL is the Supabase Auth
   callback, usually `https://<project-ref>.supabase.co/auth/v1/callback`.
3. In Supabase Auth provider settings, configure Apple with the Services ID,
   Team ID, Key ID, and signing key. Keep the signing key in the provider
   dashboard/secret manager. Supabase's Apple OAuth client secret must be
   rotated on Apple's six-month schedule.
4. Add `evidrilo://auth/callback` to the Supabase project's allowed Auth
   redirect URLs. This is separate from Apple's return URL.
5. Enable **Allow manual linking** in the Supabase project's Auth settings if
   explicit linking from an already signed-in account is required. Check the
   current dashboard setting before enabling the app flag; the client reports
   setup-required when Supabase rejects manual linking.
6. After the provider is configured and verified, set the build flag:
   - Android: `supabaseAppleAuthEnabled=true` in a local Gradle property or
     `local.properties`.
   - iOS: `SUPABASE_APPLE_AUTH_ENABLED=true` in the local `Config.xcconfig`.
   - JVM: `-Devidrilo.supabaseAppleAuthEnabled=true` or
     `SUPABASE_APPLE_AUTH_ENABLED=true`.

Apple is intentionally disabled in the current configuration. The button is
shown only when the Supabase client is configured and the relevant build flag
is true. Google remains independently controlled by
`supabaseGoogleAuthEnabled` / `SUPABASE_GOOGLE_AUTH_ENABLED`.

## Account behavior and limits

The stable Supabase user ID is the Evidrilo account key. Linking from Account
settings keeps the signed-in account active and accepts the Apple identity only
after the callback returns the same user ID and a verified Apple identity. The
screen displays the current verified account email when available and asks the
student to confirm before opening Apple. Apple can return a private relay email;
the app does not use that email to switch accounts. If the saved session is an
older record without an email, the active account ID is still checked by the
provider flow.

Supabase Auth automatically links OAuth identities that return the same
verified email to one Supabase user. The mobile client cannot turn off this
managed-auth behavior. It can prevent app-side account switching or merging and
verifies the same user ID during explicit linking, but it cannot promise that
same-email provider sign-in always waits for the in-app confirmation. If strict
consent before any same-email linking becomes a release requirement, revisit
the managed Auth design before launch.

The current browser OAuth flow requests Apple's name scope but does not receive
Apple's full name. The app uses verified email for the account display and
doesn't claim to save Apple's name. A separate optional profile step would be
needed to collect a name.

Apple provider credentials and runtime flows are deliberately not configured
or required while this decision remains in effect. If Apple sign-in is
reactivated, verify the provider on each target platform, callback and
cancellation paths, private relay email, same-user linking, duplicate-identity
conflict, and provider-disabled recovery. Before an iOS App Store submission,
review Apple's current [App Store Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
for Sign in with Apple requirements.

## Official references

- [Supabase: Sign in with Apple](https://supabase.com/docs/guides/auth/social-login/auth-apple)
- [Supabase: Identity linking](https://supabase.com/docs/guides/auth/auth-identity-linking)
- [Supabase: Redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls)
