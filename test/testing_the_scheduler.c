/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR "Sahil"

int main()
{
	int i, j;

	//use two loops
	  for(i=0;i<10;i++) {
		for(j=0; j < 100; j++){
			Print();
			}
		Sleep(10);
  		}	
}
