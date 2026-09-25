/* Minimal waf-equivalent configuration for building talloc 2.4.x on
 * Android (bionic, API 24+) without running upstream's waf configure.
 *
 * Every define reflects a capability that exists in bionic since at
 * least API 24.  Generated config.h from a waf run on Android looks
 * essentially like this (see termux-packages libtalloc cross-answers).
 *
 * NOTE: -D__STDC_WANT_LIB_EXT1__=1 must be passed on the compiler
 * command line; replace.h errors out without it.
 */
#ifndef _MSI_TALLOC_CONFIG_H
#define _MSI_TALLOC_CONFIG_H

/* --- headers present on bionic (gates replace.h includes) --- */
#define HAVE_UNISTD_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_DLFCN_H 1
#define HAVE_LIMITS_H 1
#define HAVE_SYS_PARAM_H 1
#define HAVE_STDBOOL_H 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_MALLOC_H 1

/* --- real types exist in <stdint.h>/<stdbool.h>: suppress the
 *     fallback typedefs inside replace.h --- */
#define HAVE_BOOL 1
#define HAVE_INTPTR_T 1
#define HAVE_UINTPTR_T 1
#define HAVE_PTRDIFF_T 1

/* --- capabilities talloc.c branches on --- */
#define HAVE_VA_COPY 1
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define HAVE_SYS_AUXV_H 1
#define HAVE_GETAUXVAL 1

/* --- errno declarations --- */
#define HAVE_DECL_EWOULDBLOCK 1

/* --- posix functions bionic provides (suppress replace.h fallback
 *     declarations/typedefs; without these, replace.h silently maps the
 *     calls to rep_* replacements that only exist in lib/replace) --- */
#define HAVE_MEMMOVE 1
#define HAVE_STRDUP 1
#define HAVE_STRNLEN 1
#define HAVE_VSNPRINTF 1
#define HAVE_C99_VSNPRINTF 1
#define HAVE_USLEEP 1

/* --- talloc version being compiled (upstream waf passes these) --- */
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 3

#endif /* _MSI_TALLOC_CONFIG_H */
