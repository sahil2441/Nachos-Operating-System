#include "syscall.h"

int main()
{
	int i;
	Mkdir("/1");
	Mkdir("/2");
	Mkdir("/3");
	Mkdir("/4");
	Rmdir("/1");
}