export interface ResourceHooks {
  getResource: (name: string) => string | null;
  getSystemResource?: (name: string) => string | null;
  getSystemResources?: (name: string) => string[] | null;
  getResources?: (name: string) => string[] | null;
  getResourceAsStream?: (name: string) => boolean;
  getSystemResourceAsStream?: (name: string) => boolean;
  getResourceBytes?: (name: string) => Uint8Array | number[] | null;
  getSystemResourceBytes?: (name: string) => Uint8Array | number[] | null;
}

export interface PlatformBridge {
  print?: (text: string) => void;
  println?: (text: string) => void;
  resources?: ResourceHooks;
  nativeLibraries?: Record<string, string>;
}
