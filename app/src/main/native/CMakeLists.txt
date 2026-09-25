#
# MSI native components — full source build (F-Droid compatible)
# ==============================================================
#
# Builds everything that used to be shipped as prebuilt binaries in
# jniLibs/ and assets/proot/ directly from the vendored upstream sources:
#
#   libtalloc.so          <- talloc 2.4.3        (talloc/, LGPL-3.0)
#   libandroid-shmem.so   <- libandroid-shmem 0.7 (android-shmem/, BSD-3)
#   libproot.so           <- proot 5.1.107.92    (proot/, GPL-2.0)
#                            [Termux fork; PIE *executable*, packaged as
#                             lib*.so so Android extracts it to
#                             nativeLibraryDir and lets it be exec()ed]
#   libproot-loader.so    <- proot's freestanding loader (same source tree)
#                            [static non-PIE executable, fixed load address]
#
# Flags mirror the Termux package recipes termux-packages uses to build
# the original binaries:
#   proot:   make -C src PROOT_WITH_LIBANDROID_SHMEM=true
#            CPPFLAGS += -DARG_MAX=131072 -DVERSION="<version>"
#            PROOT_UNBUNDLE_LOADER=<prefix>/libexec/proot
#   talloc:  waf-free equivalent of ./configure --cross-compile with the
#            bionic feature set expressed in talloc-config/config.h
#   shmem:   upstream Makefile flags (-std=c11, version script, -llog -landroid)
#
cmake_minimum_required(VERSION 3.22.1)
project(msi_native C)

if(NOT ANDROID)
    message(FATAL_ERROR "This project must be configured with the Android NDK toolchain file")
endif()
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# AGP sets CMAKE_LIBRARY_OUTPUT_DIRECTORY; provide a fallback for
# standalone NDK invocations (e.g. validation builds).
if(NOT CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/libs/${ANDROID_ABI})
endif()
file(MAKE_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})

# CMake's default Android link line appends -lm/-ldl, which adds DT_NEEDED
# entries the reference binaries do not carry.  The NDK clang driver links
# libc implicitly, so the standard-library set can be empty.
set(CMAKE_C_STANDARD_LIBRARIES "")

set(PROOT_SRC   ${CMAKE_CURRENT_SOURCE_DIR}/proot/src)
set(TALLOC_SRC  ${CMAKE_CURRENT_SOURCE_DIR}/talloc)
set(SHMEM_SRC   ${CMAKE_CURRENT_SOURCE_DIR}/android-shmem)

include(CheckLinkerFlag)

###############################################################################
# libtalloc.so  (hierarchical reference-counted memory pool, PRoot dependency)
#
# Packaged under the name libtalloc.so (jniLibs naming rules) but carries the
# upstream soname libtalloc.so.2, matching the SONAME the prebuilt PRoot
# binary was linked against.  PRootEngine.ensureCompatLibs() copies it to
# <filesDir>/proot-libs/libtalloc.so.2 and exposes that dir via LD_LIBRARY_PATH.
###############################################################################
add_library(talloc SHARED ${TALLOC_SRC}/talloc.c)
# Soname is set explicitly below (upstream libtalloc.so.2) instead of
# CMake's default derived from the output name (libtalloc.so).
set_target_properties(talloc PROPERTIES
    OUTPUT_NAME talloc
    NO_SONAME ON)
target_include_directories(talloc PUBLIC ${TALLOC_SRC}) # proot includes <talloc.h>
target_include_directories(talloc PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/talloc-config
    ${TALLOC_SRC}/lib/replace)
target_compile_definitions(talloc PRIVATE
    HAVE_CONFIG_H
    __STDC_WANT_LIB_EXT1__=1)
target_compile_options(talloc PRIVATE -O2 -Wall)
target_link_options(talloc PRIVATE -Wl,-soname,libtalloc.so.2 -Wl,-z,noexecstack -Wl,--as-needed)

###############################################################################
# libandroid-shmem.so  (SysV shm emulation over ashmem/memfd, PRoot dependency)
#
# Built with the upstream repo's own flags: -std=c11, exports.txt version
# script, links against liblog + libandroid.  The SONAME-less filename is
# exactly what PRoot's DT_NEEDED entry references.
###############################################################################
add_library(android-shmem SHARED ${SHMEM_SRC}/shmem.c)
set_target_properties(android-shmem PROPERTIES OUTPUT_NAME android-shmem)
target_compile_definitions(android-shmem PRIVATE
    # _PATH_TMP does not exist in the NDK bionic sysroot; the original
    # binaries (built against the Termux environment) embed the Termux
    # prefix tmp path — reproduced verbatim for identical runtime behaviour.
    _PATH_TMP="/data/data/com.termux/files/usr/tmp")
target_include_directories(android-shmem PRIVATE ${SHMEM_SRC})
target_compile_options(android-shmem PRIVATE -std=c11 -O2 -Wall -Wextra)
target_link_options(android-shmem PRIVATE
    -Wl,--version-script=${SHMEM_SRC}/exports.txt
    -Wl,-z,noexecstack
    -Wl,--as-needed)
target_link_libraries(android-shmem PRIVATE log android)

###############################################################################
# proot loader  (tiny freestanding ELF trampoline used for execve emulation)
#
# Per upstream src/GNUmakefile:
#   LOADER_CFLAGS   = -fPIC -ffreestanding
#   LOADER_LDFLAGS  = -static -nostdlib -Wl,-Ttext=<LOADER_ADDRESS>,--build-id=none,-z,noexecstack
#                     (plus -Wl,--rosegment when the linker supports it)
# LOADER_ADDRESS comes from proot's src/arch.h for the target architecture.
#
# The loader is linked through explicit compiler invocations so that no
# driver-default libraries/PIE flags leak in, then copied to the packaging
# directory as libproot-loader.so.  PRootEngine passes it to proot through
# the PROOT_LOADER environment variable.
#
# NOTE on 32-bit guest support: on arm64/x86_64 upstream also builds a
# "loader-m32" for 32-bit guests.  A 64-bit NDK toolchain cannot produce it
# (no -m32), the original shipped binaries do not contain one either, and
# this app only ever enters a rootfs matching the device ABI, so it is not
# built here — same runtime behaviour as the prebuilt binaries.
###############################################################################
set(LOADER_DIR ${CMAKE_CURRENT_BINARY_DIR}/loader-${ANDROID_ABI})

# LOADER_ADDRESS from proot/src/arch.h
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(LOADER_ADDRESS 0x2000000000)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(LOADER_ADDRESS 0x20000000)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(LOADER_ADDRESS 0x600000000000)
else()
    message(FATAL_ERROR "Unsupported ABI for the proot loader: ${ANDROID_ABI}")
endif()

# NDK r29+ lld supports --rosegment; probe it exactly like upstream does.
check_linker_flag(C "-Wl,--rosegment" LINKER_SUPPORTS_ROSEGMENT)

# Compiler with NDK target/sysroot for manual loader invocations.
set(LOADER_CC ${CMAKE_C_COMPILER})
if(DEFINED CMAKE_C_COMPILER_TARGET)
    set(LOADER_TRIPLE ${CMAKE_C_COMPILER_TARGET})
elseif(DEFINED ANDROID_LLVM_TRIPLE)
    set(LOADER_TRIPLE ${ANDROID_LLVM_TRIPLE})
else()
    message(FATAL_ERROR "Cannot determine the Android target triple")
endif()
set(LOADER_TARGET_FLAGS --target=${LOADER_TRIPLE} --sysroot=${CMAKE_SYSROOT})

set(LOADER_OUT ${LOADER_DIR}/proot-loader)
# Readelf input for the arm64 loader-info generation must be the UNSTRIPPED
# binary (upstream also reads the unstripped loader/loader).
set(LOADER_UNSTRIPPED ${LOADER_DIR}/proot-loader-unstripped)
add_custom_command(
    OUTPUT ${LOADER_OUT}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${LOADER_DIR}
    COMMAND ${LOADER_CC} ${LOADER_TARGET_FLAGS} -fPIC -ffreestanding -I${PROOT_SRC}
            -c ${PROOT_SRC}/loader/loader.c -o ${LOADER_DIR}/loader.o
    COMMAND ${LOADER_CC} ${LOADER_TARGET_FLAGS} -fPIC -ffreestanding -I${PROOT_SRC}
            -c ${PROOT_SRC}/loader/assembly.S -o ${LOADER_DIR}/assembly.o
    COMMAND ${LOADER_CC} ${LOADER_TARGET_FLAGS}
            -o ${LOADER_OUT} ${LOADER_DIR}/loader.o ${LOADER_DIR}/assembly.o
            -static -nostdlib -no-pie
            -Wl,--build-id=none
            -Wl,-Ttext=${LOADER_ADDRESS}
            -Wl,-z,noexecstack
            $<$<BOOL:${LINKER_SUPPORTS_ROSEGMENT}>:-Wl,--rosegment>
    COMMAND ${CMAKE_COMMAND} -E copy ${LOADER_OUT} ${LOADER_UNSTRIPPED}
    COMMAND ${CMAKE_STRIP} ${LOADER_OUT}
    COMMAND ${CMAKE_COMMAND} -E copy ${LOADER_OUT}
            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot-loader.so
    DEPENDS ${PROOT_SRC}/loader/loader.c
            ${PROOT_SRC}/loader/assembly.S
    VERBATIM)
add_custom_target(proot-loader ALL DEPENDS ${LOADER_OUT})

# Package the loader through an imported target so AGP picks it up.
add_library(proot-loader-packaged SHARED IMPORTED)
set_target_properties(proot-loader-packaged PROPERTIES
    IMPORTED_LOCATION ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot-loader.so)
add_dependencies(proot-loader-packaged proot-loader)

###############################################################################
# libproot.so  (PRoot itself — PIE executable packaged as a lib*.so)
#
# Sources = the OBJECTS list of upstream src/GNUmakefile, verbatim.
# Feature config comes from proot-config/build.h (HAVE_PROCESS_VM /
# HAVE_SECCOMP_FILTER); VERSION and the unbundle loader path mirror the
# Termux recipe; WITH_LIBANDROID_SHMEM matches PROOT_WITH_LIBANDROID_SHMEM.
###############################################################################
set(PROOT_SOURCES
    cli/cli.c
    cli/proot.c
    cli/note.c
    execve/enter.c
    execve/exit.c
    execve/shebang.c
    execve/elf.c
    execve/ldso.c
    execve/auxv.c
    execve/aoxp.c
    path/binding.c
    path/glue.c
    path/canon.c
    path/f2fs-bug.c
    path/path.c
    path/proc.c
    path/temp.c
    syscall/seccomp.c
    syscall/syscall.c
    syscall/chain.c
    syscall/enter.c
    syscall/exit.c
    syscall/sysnum.c
    syscall/socket.c
    syscall/heap.c
    syscall/rlimit.c
    syscall/pipe_shadow.c
    tracee/tracee.c
    tracee/mem.c
    tracee/reg.c
    tracee/event.c
    tracee/seccomp.c
    tracee/statx.c
    ptrace/ptrace.c
    ptrace/user.c
    ptrace/wait.c
    extension/extension.c
    extension/ashmem_memfd/ashmem_memfd.c
    extension/kompat/kompat.c
    extension/fake_id0/chown.c
    extension/fake_id0/chroot.c
    extension/fake_id0/getsockopt.c
    extension/fake_id0/sendmsg.c
    extension/fake_id0/socket.c
    extension/fake_id0/open.c
    extension/fake_id0/unlink.c
    extension/fake_id0/rename.c
    extension/fake_id0/chmod.c
    extension/fake_id0/utimensat.c
    extension/fake_id0/access.c
    extension/fake_id0/exec.c
    extension/fake_id0/link.c
    extension/fake_id0/symlink.c
    extension/fake_id0/mk.c
    extension/fake_id0/stat.c
    extension/fake_id0/helper_functions.c
    extension/fake_id0/fake_id0.c
    extension/hidden_files/hidden_files.c
    extension/mountinfo/mountinfo.c
    extension/port_switch/port_switch.c
    extension/sysvipc/sysvipc.c
    extension/sysvipc/sysvipc_msg.c
    extension/sysvipc/sysvipc_sem.c
    extension/sysvipc/sysvipc_shm.c
    extension/link2symlink/link2symlink.c
    extension/fix_symlink_size/fix_symlink_size.c
)
list(TRANSFORM PROOT_SOURCES PREPEND ${PROOT_SRC}/)

# arm64 carries the pokedata workaround, which needs the generated
# loader-info.c (symbol offsets of the freshly built loader), like
# upstream: readelf -s loader | awk -f loader/loader-info.awk
if(ANDROID_ABI STREQUAL "arm64-v8a")
    find_program(LOADER_READELF NAMES llvm-readelf readelf
        HINTS ${ANDROID_TOOLCHAIN_ROOT}/bin)
    find_program(LOADER_AWK NAMES awk gawk mawk REQUIRED)
    set(LOADER_INFO_C ${CMAKE_CURRENT_BINARY_DIR}/loader-info.c)
    # loader-info-portable.awk mirrors upstream's loader-info.awk without the
    # gawk-only strtonum() extension (see file header).
    add_custom_command(
        OUTPUT ${LOADER_INFO_C}
        COMMAND ${LOADER_READELF} -s ${LOADER_UNSTRIPPED}
                | ${LOADER_AWK} -f ${CMAKE_CURRENT_SOURCE_DIR}/loader-info-portable.awk
                > ${LOADER_INFO_C}
        DEPENDS ${LOADER_OUT} ${CMAKE_CURRENT_SOURCE_DIR}/loader-info-portable.awk
        VERBATIM)
    add_custom_target(proot-loader-info DEPENDS ${LOADER_INFO_C})
    list(APPEND PROOT_SOURCES ${LOADER_INFO_C})
endif()

add_executable(proot ${PROOT_SOURCES})
set_target_properties(proot PROPERTIES OUTPUT_NAME proot)
target_include_directories(proot PRIVATE
    ${PROOT_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}/shmem-include
    ${CMAKE_CURRENT_SOURCE_DIR}/proot-config)
target_compile_definitions(proot PRIVATE
    _GNU_SOURCE
    _FILE_OFFSET_BITS=64
    ARG_MAX=131072
    VERSION="5.1.107.92"
    PROOT_UNBUNDLE_LOADER="/data/data/com.termux/files/usr/libexec/proot"
    WITH_LIBANDROID_SHMEM)
# clang >= 16 promotes implicit function declarations (e.g. strcmp/memset in
# extension/ashmem_memfd/ashmem_memfd.c, shipped as-is in the v5.1.107.92 tag)
# from warnings to hard errors; older toolchains that produced this exact tag
# only warned.  Keep the sources pristine and restore warning severity.
target_compile_options(proot PRIVATE -O2 -Wall -Wextra
    -Wno-error=implicit-function-declaration)
target_link_options(proot PRIVATE -Wl,-z,noexecstack -Wl,--as-needed)
target_link_libraries(proot PRIVATE talloc android-shmem)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # loader-info.c is generated by a custom command in this directory:
    # make sure it exists before proot's sources are compiled, and that the
    # loader itself is built first (it is the readelf input).
    add_dependencies(proot proot-loader proot-loader-info)
endif()

# Package the proot executable as libproot.so (Android only extracts and
# permits exec() of lib*.so files from jniLibs -> nativeLibraryDir).
add_custom_command(TARGET proot POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
            $<TARGET_FILE:proot>
            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot.so
    VERBATIM)

add_library(proot-packaged SHARED IMPORTED)
set_target_properties(proot-packaged PROPERTIES
    IMPORTED_LOCATION ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot.so)
add_dependencies(proot-packaged proot)
