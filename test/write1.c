#include "syscall.h"



int main()
{
  OpenFileId fd;
  char *buf = "Sahi jain Stony Brook University";

  fd = Open("create-test");
  Write(buf, 15, fd);
}

