# j2me-web

Run J2ME MIDlets in modern browsers.

j2me-web combines a single-threaded Rust/WebAssembly JVM with browser adapters
for canvas rendering, input, audio, networking, and persistent RMS storage. It
supports MIDP/LCDUI applications, M3G (JSR-184) content, and common device
extensions.

> j2me-web is a compatibility runtime, not a source of MIDlet archives. Bring
> your own JAR files and ensure you have the right to use them.

## Install

```sh
pnpm add j2me-web
```

## Quick start

Create a canvas, load a MIDlet JAR, and start the runtime:

```ts
import { J2MEWeb } from 'j2me-web';

const canvas = document.querySelector<HTMLCanvasElement>('#game');
if (!canvas) throw new Error('Missing game canvas');

canvas.width = 240;
canvas.height = 320;

const response = await fetch('/midlets/example.jar');
if (!response.ok) throw new Error(`Could not load JAR: ${response.status}`);

const j2me = new J2MEWeb({
  canvas,
  jar: await response.arrayBuffer(),
  midletId: 'example',
});

await j2me.start();
```

Pass a `<canvas>` and JAR data to `J2MEWeb`.

## Features

- MIDP 2.0 / LCDUI UI, including forms, lists, commands, `Canvas`,
  `GameCanvas`, images, fonts, and pointer/key events.
- WebGPU rendering for LCDUI graphics and M3G / JSR-184 3D content.
- Live save/load states and fast in-memory rewind snapshots.
- IndexedDB-backed RecordStore (RMS), `fetch`-based networking, and browser
  audio support.
- Keyboard/pointer injection, pause/resume, screenshots, performance data,
  soft-key callbacks, and network interception.
- Nokia UI, DirectGraphics, sound, vibra, and related device extensions.

## Browser requirements

j2me-web is browser-only and requires a secure context with WebGPU support. It
also uses WebAssembly, DOM APIs, `fetch`, `MessageChannel`,
`performance.now()`, and IndexedDB. Audio uses the Web Audio API and
`HTMLAudioElement`; MIDI additionally requires `AudioWorklet`.

There is no Canvas 2D, WebGL, or non-WebGPU rendering fallback.

## API

`J2MEWeb` provides:

- `start()`, `pause()`, `resume()`, and `stop()` lifecycle control;
- `input.triggerKeyPressed()` / `triggerKeyReleased()` and pointer input
  methods;
- `saveState()`, `loadState()`, `saveStateFast()`,
  `captureRewindSnapshot()`, and `restoreRewindSnapshot()`;
- `screenshot()`, `downloadScreenshot()`, performance metrics, fullscreen,
  speed controls, and soft-key label callbacks.

For the full source, examples, and compatibility details, visit the
[j2me-web repository](https://github.com/fnknzzz/j2me-web).

## License

MIT.
