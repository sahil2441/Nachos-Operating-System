#include "syscall.h"

int main()
{
	char* name="sahil";
	int* size;
	int* address=Mmap(name,size);
	Munmap(address);
}