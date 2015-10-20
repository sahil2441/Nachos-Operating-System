
#include "syscall.h"
#define STR "Write Syscall"

int main()
{
	int pid=Exec("name");
	Join(pid);
}