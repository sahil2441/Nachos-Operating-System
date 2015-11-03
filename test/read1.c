#include "syscall.h"

int main()
{
  OpenFileId fd;
  char buf[10];
  int num;

  fd = Open("create-test");
  num = Read(buf, 10, fd);
  Close(fd);
}
