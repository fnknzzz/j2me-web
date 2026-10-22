#ifndef __M3G_WASM_STRING_H__
#define __M3G_WASM_STRING_H__

#include <stddef.h>

void *memcpy(void *dst, const void *src, size_t size);
void *memmove(void *dst, const void *src, size_t size);
void *memset(void *ptr, int value, size_t size);
int memcmp(const void *left, const void *right, size_t size);
char *strstr(const char *haystack, const char *needle);

#endif /* __M3G_WASM_STRING_H__ */
