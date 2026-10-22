#ifndef __M3G_WASM_STDDEF_H__
#define __M3G_WASM_STDDEF_H__

typedef unsigned long size_t;

#ifndef NULL
#define NULL 0
#endif

#ifndef offsetof
#define offsetof(type, member) ((size_t)&(((type *)0)->member))
#endif

#endif /* __M3G_WASM_STDDEF_H__ */
