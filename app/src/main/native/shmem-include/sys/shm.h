/* Forwarding header: exposes the vendored android-shmem shm.h as
 * <sys/shm.h>, mirroring what the Termux libandroid-shmem package
 * installs into the toolchain include tree (bionic does not provide
 * SysV shared memory).  Do not edit; edit ../android-shmem/shm.h. */
#include "../../android-shmem/shm.h"
