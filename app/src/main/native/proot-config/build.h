#ifndef BUILD_H
#define BUILD_H
/*
 * Auto-configuration header for PRoot, replicated statically for the
 * CMake/NDK build.
 *
 * Upstream (src/GNUmakefile) generates this file at build time by
 * compile-probing two optional kernel/bionic features.  Both probes
 * succeed with any modern NDK against bionic API 24+:
 *
 *   - process_vm_readv/process_vm_writev:  bionic API 23+
 *   - seccomp filter structs and prctl():  bionic API 21+
 *
 * VERSION is intentionally NOT defined here: it is passed on the
 * compiler command line (-DVERSION="5.1.107.92"), mirroring the
 * Termux package recipe.
 */
#define HAVE_PROCESS_VM
#define HAVE_SECCOMP_FILTER
#endif /* BUILD_H */
