#include "syscall.h"

int main()
{
  OpenFileId fd;
  char buf[5];
  int num;

  fd = Open("create-test");
  num = Read(buf, 5, fd);
  Close(fd);
}
