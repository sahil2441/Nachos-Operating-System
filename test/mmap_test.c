#include "syscall.h"

int main()
{
	char* name="root";
	int* size=10;
	Mmap(name,size);
}