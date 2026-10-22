import { stdin as input, stdout as output } from 'node:process';
import readline from 'node:readline/promises';

export type CapturePlatform = {
  name: string;
  aliases: string[];
  comparePlatform: string;
  captureScript: 'capture-web.js';
  captureArgs: string[];
  excludedTags?: string[];
};

export const CAPTURE_PLATFORMS: CapturePlatform[] = [
  {
    name: 'webgpu',
    aliases: ['webgpu', 'web-webgpu', 'web', 'wasm', 'rust'],
    comparePlatform: 'web-webgpu',
    captureScript: 'capture-web.js',
    captureArgs: ['--platform', 'web-webgpu'],
  },
];

export function parsePlatformSelection(args: string[]): {
  platform?: CapturePlatform;
  restArgs: string[];
} {
  if (args.length === 0) {
    return { restArgs: [] };
  }

  const normalizedArgs = args[0] === '--' ? args.slice(1) : args;
  if (normalizedArgs.length === 0) {
    return { restArgs: [] };
  }

  const first = normalizedArgs[0];
  if (!first.startsWith('-')) {
    return {
      platform: resolvePlatform(first),
      restArgs: normalizedArgs.slice(1),
    };
  }

  const platformIndex = normalizedArgs.indexOf('--platform');
  if (platformIndex >= 0) {
    const value = normalizedArgs[platformIndex + 1];
    if (!value) {
      throw new Error('--platform requires a value');
    }
    return {
      platform: resolvePlatform(value),
      restArgs: [
        ...normalizedArgs.slice(0, platformIndex),
        ...normalizedArgs.slice(platformIndex + 2),
      ],
    };
  }

  return { restArgs: normalizedArgs };
}

export function selectedPlatforms(
  platform?: CapturePlatform,
): CapturePlatform[] {
  return platform ? [platform] : CAPTURE_PLATFORMS;
}

export async function selectPlatforms(
  args: string[],
): Promise<{ platforms: CapturePlatform[]; restArgs: string[] }> {
  const { platform, restArgs, all } = parsePlatformSelectionWithAll(args);
  if (platform) {
    return { platforms: [platform], restArgs };
  }
  if (all) {
    return { platforms: CAPTURE_PLATFORMS, restArgs };
  }
  if (!input.isTTY || !output.isTTY) {
    return { platforms: CAPTURE_PLATFORMS, restArgs };
  }
  return { platforms: await promptPlatforms(), restArgs };
}

async function promptPlatforms(): Promise<CapturePlatform[]> {
  const choices = CAPTURE_PLATFORMS.map(
    (platform, index) =>
      `  ${index + 1}) ${platform.name} (${platform.comparePlatform})`,
  ).join('\n');
  const rl = readline.createInterface({ input, output });
  try {
    const answer = (
      await rl.question(
        `请选择要运行的平台：\n${choices}\n  a) all\n输入编号/名称，可逗号多选，回车默认 all: `,
      )
    ).trim();
    if (
      answer === '' ||
      answer.toLowerCase() === 'a' ||
      answer.toLowerCase() === 'all'
    ) {
      return CAPTURE_PLATFORMS;
    }
    const selected = answer
      .split(',')
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        const index = Number(part);
        if (
          Number.isInteger(index) &&
          index >= 1 &&
          index <= CAPTURE_PLATFORMS.length
        ) {
          return CAPTURE_PLATFORMS[index - 1];
        }
        return resolvePlatform(part);
      });
    return uniquePlatforms(selected);
  } finally {
    rl.close();
  }
}

function parsePlatformSelectionWithAll(args: string[]): {
  platform?: CapturePlatform;
  restArgs: string[];
  all: boolean;
} {
  const normalizedArgs = args[0] === '--' ? args.slice(1) : args;
  if (normalizedArgs.length === 0) {
    return { restArgs: [], all: false };
  }

  const first = normalizedArgs[0];
  if (!first.startsWith('-')) {
    if (first.toLowerCase() === 'all') {
      return { restArgs: normalizedArgs.slice(1), all: true };
    }
    return {
      platform: resolvePlatform(first),
      restArgs: normalizedArgs.slice(1),
      all: false,
    };
  }

  const allIndex = normalizedArgs.indexOf('--all');
  const withoutAll =
    allIndex >= 0
      ? [
          ...normalizedArgs.slice(0, allIndex),
          ...normalizedArgs.slice(allIndex + 1),
        ]
      : normalizedArgs;
  const { platform, restArgs } = parsePlatformSelection(withoutAll);
  return { platform, restArgs, all: allIndex >= 0 };
}

function uniquePlatforms(platforms: CapturePlatform[]): CapturePlatform[] {
  return CAPTURE_PLATFORMS.filter((candidate) => platforms.includes(candidate));
}

export function resolvePlatform(name: string): CapturePlatform {
  const normalized = name.toLowerCase();
  const platform = CAPTURE_PLATFORMS.find((candidate) =>
    candidate.aliases.includes(normalized),
  );
  if (!platform) {
    const aliases = CAPTURE_PLATFORMS.map((candidate) => candidate.name).join(
      ', ',
    );
    throw new Error(`Unknown platform "${name}". Expected one of: ${aliases}`);
  }
  return platform;
}
