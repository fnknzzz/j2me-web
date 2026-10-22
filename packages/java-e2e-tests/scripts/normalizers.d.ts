export declare const normalizeVolatile: (s: string) => string;

export declare const normalizers: {
  volatile: typeof normalizeVolatile;
};

export declare const applyNormalizers: (
  output: string,
  names: string[],
) => string;

export declare const applyAllNormalizers: (output: string) => string;
