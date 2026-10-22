#!/usr/bin/env node

export const normalizeVolatile = (s) => {
  const counters = {};

  const getNextId = (type, value) => {
    if (!counters[type]) {
      counters[type] = {};
    }
    const typeMap = counters[type];
    if (!(value in typeMap)) {
      typeMap[value] = Object.keys(typeMap).length + 1;
    }
    return typeMap[value];
  };

  let result = s.replace(
    /(\[+[BCDFIJSZ]|\[+L[\w$]+(?:\.[\w$]+)*;|[a-zA-Z_$][\w$]*(?:\.[\w$]+)*)@([0-9a-f]*[0-9][0-9a-f]*)(?![0-9a-f])/g,
    (_match, className, hash) => {
      return `${className}@<~hash@${getNextId('hash', hash)}>`;
    },
  );

  result = result.replace(/<~(\w+):(.*?)~>/g, (_, type, value) => {
    if (type === 'time') {
      return '<~time>';
    }
    return `<~${type}@${getNextId(type, value)}>`;
  });

  return result;
};

export const normalizers = {
  volatile: normalizeVolatile,
};

export const applyNormalizers = (output, names) => {
  let result = output;
  for (const name of names) {
    const fn = normalizers[name];
    if (fn) {
      result = fn(result);
    }
  }
  return result;
};

export const applyAllNormalizers = (output) => {
  return applyNormalizers(output, Object.keys(normalizers));
};
