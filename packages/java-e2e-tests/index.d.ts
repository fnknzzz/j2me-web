export interface TestCase {
  name: string;
  readonly entries: {
    name: string;
    data: ArrayBuffer;
    isClass: boolean;
  }[];
  readonly jar: ArrayBuffer;
  readonly output: string;
}

export declare const cldcTestCases: TestCase[];

/**
 * CLDC 自定义断言策略 Map
 * key: 用例名 (不含 .jar 后缀)
 * value: 断言函数，接收实际输出，不匹配时抛出 Error
 */
export declare const cldcValidators: Record<string, (output: string) => void>;
