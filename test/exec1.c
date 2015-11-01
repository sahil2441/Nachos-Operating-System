/* Basic test of Exec() system call */

#include "syscall.h"

int
main()
{
  Exec("round_robin_test");
}
