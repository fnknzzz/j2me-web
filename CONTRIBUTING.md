# Contributing

Thanks for contributing to j2me-web.

## Requirements

- Node.js 22.20 or newer
- pnpm 12.14.0
- Rust stable with the `wasm32-unknown-unknown` target
- JDK 8 for building J2ME runtime and test JARs
- Chromium for visual snapshot tests

## Setup

```sh
pnpm install
```

## Checks

```sh
pnpm biome
pnpm test
pnpm pack:check
```

For focused work, `pnpm test:rust`, `pnpm test:web`, and `pnpm demo` are also
available. The demo is a static browser app: import a JAR locally rather than
adding game archives to the repository.

## Pull requests

Keep changes focused, include relevant tests, and run formatting before opening
a pull request. Do not add proprietary MIDlet archives, credentials, or other
non-redistributable content.
