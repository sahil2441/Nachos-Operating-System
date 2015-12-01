#include "syscall.h"

int main()
{
	char* name="sahil";
	int size=0;
	int* address=Mmap(name,&size);
	
	address[0]='k';
	
//	Munmap(address);
}