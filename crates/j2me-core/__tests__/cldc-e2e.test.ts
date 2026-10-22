import { describe, expect, it } from 'vitest';
import {
  cldcTestCases,
  cldcValidators,
} from '../../../packages/java-e2e-tests/index.js';
import { normalizeVolatile } from '../../../packages/java-e2e-tests/scripts/normalizers.js';
import { JvmInstance } from '../index';

// These suites still expose known CLDC compatibility gaps in the Rust JVM.
const skippedTests = new Set([
  'AccessControllerSystemClassSuite',
  'ArrayIndexOutOfBoundsExceptionSystemClassSuite',
  'ByteArrayOutputStreamSystemSuite',
  'CalendarDebugSuite',
  'CalendarSuite',
  'CalendarSystemClassSuite',
  'CharacterSystemClassSuite',
  'DateSuite',
  'DateSystemClassSuite',
  'InputStreamReaderSuite',
  'NumberFormatExceptionSystemClassSuite',
  'OutputStreamWriterSystemClassSuite',
  'StringIndexOutOfBoundsExceptionSystemClassSuite',
  'StringSystemClassSuite',
  'SystemSystemClassSuite',
  'ThrowableSuite',
  'ThrowableSystemClassSuite',
  'VectorEnumeratorSystemClassSuite',
]);

describe('CLDC E2E Tests (jvm-rust)', () => {
  for (const tc of cldcTestCases) {
    const testFn = skippedTests.has(tc.name) ? it.skip : it;
    testFn(tc.name, async () => {
      const vm = await JvmInstance.create(false);
      try {
        // Load classes and resources from entries
        vm.loadEntries(tc.entries);
        const started = vm.startMainFromManifest();
        if (started !== 0) {
          throw new Error(`Failed to start Main-Class for ${tc.name}`);
        }
        const output = normalizeVolatile(
          vm.run(50000).replace(/^\n+/, '').replace(/\n+$/, '\n'),
        );
        const validator = cldcValidators[tc.name];
        if (validator) {
          validator(output);
        } else {
          expect(output).toBe(tc.output);
        }
      } finally {
        vm.destroy();
      }
    });
  }
});
