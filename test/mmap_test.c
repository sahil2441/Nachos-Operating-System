#include "syscall.h"

int main()
{
	char* name="sahil";
	int size=0;
	int* address1=Mmap(name,&size);
	
	address1[0]='k';		
	Munmap(address1);
}