/* Basic test of Exec() system call */

#include "syscall.h"

int
main()
{
	int i;
	for(i=0;i<500;i++){
		Print();
	}	
}
