import { describe, expect, it } from 'vitest';
import { applyNormalizers, normalizeVolatile } from '../scripts/normalizers.js';

describe('normalizeVolatile', () => {
  describe('DSL markers <~type:value~>', () => {
    it('should normalize mem values with auto-incrementing IDs', () => {
      const input = 'mem=<~mem:1912276303~>';
      expect(normalizeVolatile(input)).toBe('mem=<~mem@1>');
    });

    it('should assign same ID to same value', () => {
      const input = `m1=<~mem:1912276303~>
m2=<~mem:987654321~>
m1-again=<~mem:1912276303~>`;
      const expected = `m1=<~mem@1>
m2=<~mem@2>
m1-again=<~mem@1>`;
      expect(normalizeVolatile(input)).toBe(expected);
    });

    it('should not assign ID to time type', () => {
      const input = 'time=<~time:1705123456789~>';
      expect(normalizeVolatile(input)).toBe('time=<~time>');
    });

    it('should handle multiple types independently', () => {
      const input =
        'mem=<~mem:123~> time=<~time:456~> thread=<~thread:Thread-3~>';
      expect(normalizeVolatile(input)).toBe(
        'mem=<~mem@1> time=<~time> thread=<~thread@1>',
      );
    });

    it('should count IDs separately for different types with same value', () => {
      const input = 'a=<~mem:100~> b=<~id:100~>';
      expect(normalizeVolatile(input)).toBe('a=<~mem@1> b=<~id@1>');
    });

    it('should preserve non-volatile text', () => {
      const input = 'result=42\nmem=<~mem:123~>\ndone';
      expect(normalizeVolatile(input)).toBe('result=42\nmem=<~mem@1>\ndone');
    });
  });

  describe('ClassName@hash auto-detection', () => {
    it('should normalize simple ClassName@hash', () => {
      const input = 'java.io.PrintStream@55f96302';
      expect(normalizeVolatile(input)).toBe('java.io.PrintStream@<~hash@1>');
    });

    it('should normalize nested class ClassName$Inner@hash', () => {
      const input = 'com.example.Outer$Inner@7d4991ad';
      expect(normalizeVolatile(input)).toBe(
        'com.example.Outer$Inner@<~hash@1>',
      );
    });

    it('should assign same ID to same hash value', () => {
      const input = `obj1=Foo@abc123
obj2=Bar@def456
obj1-again=Foo@abc123`;
      const expected = `obj1=Foo@<~hash@1>
obj2=Bar@<~hash@2>
obj1-again=Foo@<~hash@1>`;
      expect(normalizeVolatile(input)).toBe(expected);
    });

    it('should handle hash followed by non-hex uppercase letter', () => {
      const input = 'DefaultToStringObject@7d4991adMIDDLECustomObject-TAIL';
      expect(normalizeVolatile(input)).toBe(
        'DefaultToStringObject@<~hash@1>MIDDLECustomObject-TAIL',
      );
    });

    it('should handle hash at end of line', () => {
      const input = 'obj=MyClass@dead1eef';
      expect(normalizeVolatile(input)).toBe('obj=MyClass@<~hash@1>');
    });

    it('should handle hash followed by punctuation', () => {
      const input = 'obj=MyClass@abc123, next';
      expect(normalizeVolatile(input)).toBe('obj=MyClass@<~hash@1>, next');
    });

    it('should handle hash in quotes', () => {
      const input = "result='obj-MyClass@abc123'";
      expect(normalizeVolatile(input)).toBe("result='obj-MyClass@<~hash@1>'");
    });

    it('should not match email-like patterns', () => {
      const input = 'email=user@example.com';
      expect(normalizeVolatile(input)).toBe('email=user@example.com');
    });

    it('should not match @hash without class name prefix', () => {
      const input = '@abc123';
      expect(normalizeVolatile(input)).toBe('@abc123');
    });

    it('should normalize primitive array type [I@hash', () => {
      const input = '[I@1a2b3c4d';
      expect(normalizeVolatile(input)).toBe('[I@<~hash@1>');
    });

    it('should normalize object array type [Ljava.lang.String;@hash', () => {
      const input = '[Ljava.lang.String;@75b84c92';
      expect(normalizeVolatile(input)).toBe('[Ljava.lang.String;@<~hash@1>');
    });

    it('should normalize multi-dimensional array type [[I@hash', () => {
      const input = '[[I@abc12345';
      expect(normalizeVolatile(input)).toBe('[[I@<~hash@1>');
    });
  });

  describe('mixed DSL and auto-detection', () => {
    it('should process auto-detection before DSL markers', () => {
      const input = 'obj=MyClass@abc123 mem=<~mem:999~>';
      expect(normalizeVolatile(input)).toBe(
        'obj=MyClass@<~hash@1> mem=<~mem@1>',
      );
    });

    it('should share hash counter between auto-detected and DSL', () => {
      const input = 'obj=MyClass@abc123 explicit=<~hash:abc123~>';
      expect(normalizeVolatile(input)).toBe(
        'obj=MyClass@<~hash@1> explicit=<~hash@1>',
      );
    });

    it('should not double-process DSL inside auto-detected result', () => {
      const input = 'obj=MyClass@abc123';
      const result = normalizeVolatile(input);
      expect(result).toBe('obj=MyClass@<~hash@1>');
      expect(result).not.toContain('<~hash@<~hash');
    });
  });
});

describe('applyNormalizers', () => {
  it('should apply volatile normalizer', () => {
    const input = 'mem=<~mem:123~>';
    expect(applyNormalizers(input, ['volatile'])).toBe('mem=<~mem@1>');
  });

  it('should skip unknown normalizers', () => {
    const input = 'mem=<~mem:123~>';
    expect(applyNormalizers(input, ['unknown', 'volatile'])).toBe(
      'mem=<~mem@1>',
    );
  });

  it('should return original when no normalizers', () => {
    const input = 'hello world';
    expect(applyNormalizers(input, [])).toBe('hello world');
  });
});
