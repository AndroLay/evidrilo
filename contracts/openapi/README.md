# OpenAPI boundary

`openapi.v1.json` is generated from `../routes.v1.json` and the public JSON
schemas. The route manifest records each route's authentication boundary and
its implementation-test anchor. Request bodies are documented only when a
versioned request schema exists.

Regenerate the committed document with:

```sh
node contracts/openapi/generate.mjs
```

Verify it is reproducible with:

```sh
node contracts/openapi/generate.mjs --check
```

The document must contain no environment secrets or raw provider payloads.
OpenAPI security descriptions do not imply that an intentionally disabled
provider or unavailable product capability is live.
