#include "syscall.h"

int main()
{
	char* name="sahil1";
	int size=0;
	int* address1=Mmap(name,&size);
	
	address1[0]='s';		
	address1[1]='a';		
	address1[2]='h';		
	address1[3]='i';		
	address1[4]='l';		
	Munmap(address1);
}