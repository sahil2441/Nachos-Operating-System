#include "syscall.h"

int main()
{
  OpenFileId fd;
  char buf[1000];
  int num;

  fd = Open("create-test");
  num = Read(buf, 1000, fd);
  Close(fd);
}
