# j2me-web Demo

This is a static browser demo for `j2me-web`. It does not ship MIDlet
archives. Use the **Import JAR file** button to select a local `.jar` file.

Imported JARs remain in the current browser's IndexedDB, so they appear again
after a refresh. Use **Delete** beside an entry to remove its local copy.
Nothing is uploaded to a server.

```sh
pnpm demo
```

The app starts at <http://localhost:3000>.

Build the static site with:

```sh
pnpm demo:build
```

The generated `dist/` directory is suitable for static hosting, including
GitHub Pages. The browser must provide WebGPU; use HTTPS or `localhost`.
