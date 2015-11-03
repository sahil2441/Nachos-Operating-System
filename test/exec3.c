/* Basic test of Exec() system call */

#include "syscall.h"

int
main()
{
	int i;
	for(i=0;i<1500;i++){
		Print();
	}	
}
