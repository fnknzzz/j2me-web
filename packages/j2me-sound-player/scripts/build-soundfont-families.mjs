import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { BasicSoundBank, SoundBankLoader } from 'spessasynth_core';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const packageDirectory = resolve(scriptDirectory, '..');
const repositoryRoot = resolve(packageDirectory, '../..');
const sourcePath = resolve(
  repositoryRoot,
  'assets/source/GeneralUser.sf2',
);
const outputDirectory = resolve(packageDirectory, 'assets/soundfonts');

const families = [
  ['piano', 0, 7],
  ['chromatic', 8, 15],
  ['organ', 16, 23],
  ['guitar', 24, 31],
  ['bass', 32, 39],
  ['strings', 40, 47],
  ['ensemble', 48, 55],
  ['brass', 56, 63],
  ['reed', 64, 71],
  ['pipe', 72, 79],
  ['synth-lead', 80, 87],
  ['synth-pad', 88, 95],
  ['synth-effects', 96, 103],
  ['ethnic', 104, 111],
  ['percussive', 112, 119],
  ['sound-effects', 120, 127],
];

const ffmpegEncoders = execFileSync('ffmpeg', ['-hide_banner', '-encoders'], {
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'ignore'],
});
const vorbisEncoder = /\blibvorbis\b/.test(ffmpegEncoders)
  ? 'libvorbis'
  : 'vorbis';
const vorbisEncoderOptions =
  vorbisEncoder === 'vorbis'
    ? [
        '-strict',
        'experimental',
        // FFmpeg's native Vorbis encoder only accepts stereo. Duplicate the
        // mono SoundFont sample; SpessaSynth intentionally decodes channel 0.
        '-ac',
        '2',
      ]
    : [];

console.log(`Using ffmpeg Vorbis encoder: ${vorbisEncoder}`);

const encodeVorbis = (audioData, sampleRate) =>
  new Promise((resolvePromise, reject) => {
    const ffmpeg = spawn(
      'ffmpeg',
      [
        '-hide_banner',
        '-loglevel',
        'error',
        '-f',
        'f32le',
        '-ar',
        String(sampleRate),
        '-ac',
        '1',
        '-i',
        'pipe:0',
        '-map_metadata',
        '-1',
        '-c:a',
        vorbisEncoder,
        ...vorbisEncoderOptions,
        '-q:a',
        '5',
        '-f',
        'ogg',
        'pipe:1',
      ],
      { stdio: ['pipe', 'pipe', 'pipe'] },
    );

    const stdout = [];
    const stderr = [];
    ffmpeg.stdout.on('data', (chunk) => stdout.push(chunk));
    ffmpeg.stderr.on('data', (chunk) => stderr.push(chunk));
    ffmpeg.stdin.on('error', (error) => {
      if (error.code !== 'EPIPE') {
        reject(error);
      }
    });
    ffmpeg.on('error', reject);
    ffmpeg.on('close', (code) => {
      if (code !== 0) {
        reject(
          new Error(
            `ffmpeg failed with code ${code}: ${Buffer.concat(stderr).toString()}`,
          ),
        );
        return;
      }
      resolvePromise(new Uint8Array(Buffer.concat(stdout)));
    });

    ffmpeg.stdin.end(
      Buffer.from(audioData.buffer, audioData.byteOffset, audioData.byteLength),
    );
  });

const isDrumPreset = (preset) => preset.isGMGSDrum || preset.bankMSB === 120;

const buildFamilyBank = (sourceBank, predicate) => {
  const bank = BasicSoundBank.copyFrom(sourceBank);
  for (const preset of [...bank.presets]) {
    if (!predicate(preset)) {
      bank.deletePreset(preset);
    }
  }
  bank.removeUnusedElements();
  bank.flush();
  return bank;
};

const sourceBuffer = await readFile(sourcePath);
const sourceArrayBuffer = sourceBuffer.buffer.slice(
  sourceBuffer.byteOffset,
  sourceBuffer.byteOffset + sourceBuffer.byteLength,
);
const sourceBank = SoundBankLoader.fromArrayBuffer(sourceArrayBuffer);

console.log(
  `Compressing ${sourceBank.samples.length} samples from ${sourceBank.soundBankInfo.name}...`,
);
let lastReportedPercent = -1;
await sourceBank.setSampleFormat({
  format: 'compressed',
  compressionFunction: encodeVorbis,
  progressFunction: (progress) => {
    const percent = Math.floor(progress * 100);
    if (percent % 5 === 0 && percent !== lastReportedPercent) {
      lastReportedPercent = percent;
      process.stdout.write(`\rSF3 compression: ${percent}%`);
    }
  },
});
process.stdout.write('\n');
const uncompressedSamples = sourceBank.samples.filter(
  (sample) => !sample.isCompressed,
);
if (uncompressedSamples.length > 0) {
  throw new Error(
    `SF3 compression failed for ${uncompressedSamples.length} samples`,
  );
}

await mkdir(outputDirectory, { recursive: true });
const manifest = {
  version: 1,
  source: sourceBank.soundBankInfo.name,
  format: 'sf3',
  compression: {
    encoder: `ffmpeg:${vorbisEncoder}`,
    quality: 5,
  },
  families: {},
};

for (const [id, programStart, programEnd] of families) {
  const bank = buildFamilyBank(
    sourceBank,
    (preset) =>
      !isDrumPreset(preset) &&
      preset.program >= programStart &&
      preset.program <= programEnd,
  );
  const binary = bank.writeSF2();
  const fileName = `${id}.sf3`;
  await writeFile(resolve(outputDirectory, fileName), new Uint8Array(binary));
  manifest.families[id] = {
    file: fileName,
    programStart,
    programEnd,
    presets: bank.presets.length,
    bytes: binary.byteLength,
  };
  console.log(`${fileName}: ${(binary.byteLength / 1024).toFixed(1)} KiB`);
  bank.destroySoundBank();
}

const drumBank = buildFamilyBank(sourceBank, isDrumPreset);
const drumBinary = drumBank.writeSF2();
await writeFile(
  resolve(outputDirectory, 'drums.sf3'),
  new Uint8Array(drumBinary),
);
manifest.families.drums = {
  file: 'drums.sf3',
  presets: drumBank.presets.length,
  bytes: drumBinary.byteLength,
};
console.log(`drums.sf3: ${(drumBinary.byteLength / 1024).toFixed(1)} KiB`);

await writeFile(
  resolve(outputDirectory, 'manifest.json'),
  `${JSON.stringify(manifest, null, 2)}\n`,
);

sourceBank.destroySoundBank();
drumBank.destroySoundBank();
