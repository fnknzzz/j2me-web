export type SizedWireFields = ReadonlyArray<readonly unknown[]>;

export interface SizedWireLayout {
  bytes: number;
  offset: Record<string, number>;
  size: Record<string, number>;
}

export function resolveSizedWireLayout(
  fields: SizedWireFields,
  layoutName: string,
): SizedWireLayout {
  let bytes = 0;
  const offset: Record<string, number> = {};
  const size: Record<string, number> = {};

  for (const field of fields) {
    const [fieldName, fieldBytes] = field;
    if (
      field.length !== 2 ||
      typeof fieldName !== 'string' ||
      fieldName.length === 0 ||
      typeof fieldBytes !== 'number' ||
      !Number.isInteger(fieldBytes) ||
      fieldBytes <= 0 ||
      fieldBytes % 4 !== 0
    ) {
      throw new Error(
        `${layoutName}.${fieldName} must be a positive 4-byte-aligned size`,
      );
    }
    if (Object.hasOwn(offset, fieldName)) {
      throw new Error(`${layoutName} has duplicate ${fieldName}`);
    }
    offset[fieldName] = bytes;
    size[fieldName] = fieldBytes;
    bytes += fieldBytes;
  }

  return { bytes, offset, size };
}
