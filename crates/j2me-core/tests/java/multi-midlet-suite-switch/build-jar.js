#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import {
  copyFileSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const fixtureDir = dirname(fileURLToPath(import.meta.url));
const packageRoot = resolve(fixtureDir, '../../..');
const repoRoot = resolve(packageRoot, '../..');
const outputDir = join(packageRoot, 'tests/jars');
const sourceFiles = [
  'SharedState.java',
  'FirstMIDlet.java',
  'SecondMIDlet.java',
  'AsyncSwitchMIDlet.java',
];
const iconFiles = ['first.png', 'second.png', 'async.png'];

const buildSuiteSwitchJar = () => {
  const root = mkdtempSync(join(tmpdir(), 'jsjvm-suite-switch-'));
  const classes = join(root, 'classes');
  const manifest = join(root, 'MANIFEST.MF');
  const jar = join(outputDir, 'SuiteSwitch.jar');
  try {
    mkdirSync(classes, { recursive: true });
    mkdirSync(outputDir, { recursive: true });
    const bootcp = join(repoRoot, 'crates/j2me-core/j2me-rt/classes.jar');
    execFileSync('javac', [
      '-encoding',
      'UTF-8',
      '-bootclasspath',
      bootcp,
      '-source',
      '1.3',
      '-target',
      '1.3',
      '-d',
      classes,
      ...sourceFiles.map((file) => join(fixtureDir, file)),
    ]);
    mkdirSync(join(classes, 'icons'), { recursive: true });
    for (const iconFile of iconFiles) {
      copyFileSync(
        join(fixtureDir, 'icons', iconFile),
        join(classes, 'icons', iconFile),
      );
    }
    writeFileSync(
      manifest,
      [
        'Manifest-Version: 1.0',
        'MIDlet-1: First,/icons/first.png,FirstMIDlet',
        'MIDlet-2: Second,/icons/second.png,SecondMIDlet',
        'MIDlet-3: Async,/icons/async.png,AsyncSwitchMIDlet',
        'MIDlet-Name: SuiteSwitch',
        'MIDlet-Vendor: JSJVM',
        'MIDlet-Version: 1.0.0',
        'MicroEdition-Configuration: CLDC-1.1',
        'MicroEdition-Profile: MIDP-2.0',
        '',
        '',
      ].join('\n'),
    );
    execFileSync('jar', ['cmf', manifest, jar, '-C', classes, '.']);
    return new Uint8Array(readFileSync(jar));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
};

const jarData = buildSuiteSwitchJar();
console.log(
  `Built ${join(outputDir, 'SuiteSwitch.jar')} (${jarData.length} bytes)`,
);
