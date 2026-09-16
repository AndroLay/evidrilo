# Evidrilo audio assets

Five short interaction effects are bundled. They are original, deterministic
mono PCM tones generated for Evidrilo in-house on 2026-09-14; no external
sample, provider payload, private voice data, or attribution obligation is
used. They are distributed under the repository MIT license.

| ID | File | Duration | Bytes | Purpose |
| --- | --- | ---: | ---: | --- |
| `SELECTION` | `effects/selection.wav` | 90 ms | 4,048 | selecting a supplied option |
| `SUCCESS` | `effects/success.wav` | 220 ms | 9,780 | successful bounded action |
| `ERROR` | `effects/error.wav` | 160 ms | 7,134 | validation or operation failure |
| `CHALLENGE_REVEAL` | `effects/challenge-reveal.wav` | 280 ms | 12,426 | evidence-change challenge reveal |
| `PREMIUM_STATE` | `effects/premium-state.wav` | 240 ms | 10,662 | premium access-state feedback |

The exact source descriptors, SHA-256 checksums, and license reference are in
`audio-manifest.json`. The effects are intentionally short and low-volume;
they are interaction feedback, not narration and not proof of natural speech.

No reviewed production narration is bundled yet. Fixed copy therefore uses
the visible text path and platform offline-TTS fallback. Strict public export
must remain closed until any future narration recording is reviewed for
pronunciation, clarity, factual wording, redistribution permission, and
device playback.

Before another asset is added, record its stable semantic ID, exact source
copy/descriptor, creator or license, redistribution terms, provenance,
format, duration, byte length, checksum, and review owner/date here. Run the
strict asset validator after every manifest change.

The exact current fixed-copy narration source is maintained in the
[narration source inventory](audio-narration-source-inventory.md). The
inventory is a handoff record only; it does not imply that those clips exist
or have passed human review.
