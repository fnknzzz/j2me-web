---
name: wtk-preview
description: Use when the user wants to preview or run a J2ME/MIDlet Java source file from this Mac workspace in a Windows WTK emulator.
---

# WTK Preview

Use this skill to send local J2ME MIDlet source files and optional resource files from Mac to a Windows WTK preview server. The server restores the submitted files, builds with WTK `preverify`, and opens WTK emulator.

## Scripts

The implementation scripts live in `tools/jar-builder/`:

- `tools/jar-builder/wtk-preview-server.js`: run on Windows.
- `tools/jar-builder/wtk-submit.js`: run on Mac from this repo.

## Windows Server

Run in an interactive Windows desktop session:

```cmd
set WTK_HOME=C:\WTK2.5.2
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
node tools\jar-builder\wtk-preview-server.js
```

The server listens on `0.0.0.0:8787` by default and opens `QwertyDevice`.
The host and port are fixed to `0.0.0.0:8787`.

## Mac Submit

Before submitting, verify `tools/jar-builder/.env.local` exists and has `WINDOWS_IP` set:

```env
# tools/jar-builder/.env.local
WINDOWS_IP=192.168.x.x
```

If `WINDOWS_IP` is missing or empty, stop and ask the user to set it, or pass `--host http://<windows-ip>:8787`.

Submit a MIDlet source:

```bash
node tools/jar-builder/wtk-submit.js \
  --name <MIDletClassName> \
  --src <path/to/MIDlet.java>
```

Optional flags:

- `--main <class>` when the MIDlet main class differs from `--name`.
- `--resources <dir>` to include resource files in the JAR root.
- `--device <device>` to override `QwertyDevice`.
- `--host http://<windows-ip>:8787` to bypass `.env.local`.

`--src` may be a single `.java` file or a directory containing `.java` files. Resource files are sent as binary content. If `--resources` is omitted and `tools/visual-snapshot/cases/res/<MIDletClassName>/` exists, `wtk-submit.js` includes that directory automatically.

Submit with resources:

```bash
node tools/jar-builder/wtk-submit.js \
  --name M3GLoaderBoardMIDlet \
  --src tools/visual-snapshot/cases/src/M3GLoaderBoardMIDlet.java \
  --resources tools/visual-snapshot/cases/res/M3GLoaderBoardMIDlet
```

## Workflow

1. Ensure the Windows server is already running.
2. Submit the source/resource package from Mac using `tools/jar-builder/wtk-submit.js`.
3. The server writes a temp source/resource tree, calls `tools/jar-builder/build-jar.bat`, then launches WTK emulator.
4. Ask the user for the WTK screenshot/log result if the Mac cannot observe the Windows desktop.

## Notes

- Send source/resources, not the Mac-built JAR, so Windows runs WTK `preverify`.
- The HTTP request contains file paths and base64 file contents. The Windows server only restores files and runs the build script; it does not need to understand Java source content.
- No authentication is used by design; only run on a trusted LAN.
- If submission fails, check `WINDOWS_IP`, Windows firewall port `8787`, and Windows `WTK_HOME`/`JAVA_HOME`.
