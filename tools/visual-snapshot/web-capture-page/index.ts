import { J2MEWeb } from 'j2me-web';

type BrowserCase = {
  id: string;
  jar: string;
  midletClass: string;
  midletIndex: number;
  width: number;
  height: number;
  waitMs: number;
};

type BrowserCases = {
  cases: BrowserCase[];
};

declare global {
  interface Window {
    __visualSnapshot: {
      ready: Promise<void>;
      getInfo(): {
        caseId: string;
        width: number;
        height: number;
        canvasSelector: string;
      };
      getPngDataUrl(): string | Promise<string>;
    };
  }
}

const ready = startCapturePage();

window.__visualSnapshot = {
  ready,
  getInfo() {
    const canvas = getCanvas();
    return {
      caseId: canvas.dataset.caseId ?? '',
      width: Number(canvas.dataset.logicalWidth ?? canvas.width),
      height: Number(canvas.dataset.logicalHeight ?? canvas.height),
      canvasSelector: '#game-canvas',
    };
  },
  getPngDataUrl() {
    const j2me = (window as unknown as { j2me: J2MEWeb }).j2me;
    if (j2me) {
      return j2me.screenshot();
    }
    const canvas = getCanvas();
    return canvas.toDataURL('image/png');
  },
};

async function startCapturePage(): Promise<void> {
  const params = new URLSearchParams(window.location.search);
  const caseId = params.get('case');
  if (!caseId) {
    throw new Error('Missing case query parameter');
  }

  const response = await fetch('/__visual_snapshot__/cases.json');
  if (!response.ok) {
    throw new Error(`Failed to load cases: ${response.status}`);
  }
  const cases = (await response.json()) as BrowserCases;
  const testCase = cases.cases.find((entry) => entry.id === caseId);
  if (!testCase) {
    throw new Error(`Unknown visual snapshot case: ${caseId}`);
  }

  document.body.style.width = `${testCase.width}px`;
  document.body.style.height = `${testCase.height}px`;

  const canvas = document.createElement('canvas');
  canvas.id = 'game-canvas';
  canvas.width = testCase.width;
  canvas.height = testCase.height;
  canvas.dataset.caseId = testCase.id;
  canvas.dataset.logicalWidth = String(testCase.width);
  canvas.dataset.logicalHeight = String(testCase.height);
  document.getElementById('root')?.appendChild(canvas);

  const jarResponse = await fetch(testCase.jar);
  if (!jarResponse.ok) {
    throw new Error(
      `Failed to load JAR for ${testCase.id}: ${jarResponse.status}`,
    );
  }
  const jar = await jarResponse.arrayBuffer();

  const j2me = new J2MEWeb({
    canvas,
    jar,
    midletNumber: testCase.midletIndex,
  });
  (window as unknown as { j2me: J2MEWeb }).j2me = j2me;
  await j2me.start();
}

function getCanvas(): HTMLCanvasElement {
  const canvas = document.getElementById('game-canvas');
  if (!(canvas instanceof HTMLCanvasElement)) {
    throw new Error('Capture canvas is not available');
  }
  return canvas;
}
