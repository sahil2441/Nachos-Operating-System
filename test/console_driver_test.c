/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR "Sahil"

int main()
{
	
	/* write STR to data file */
	Write(STR, sizeof(STR)-1,ConsoleOutput);
	Write(STR, sizeof(STR)-1,ConsoleOutput);
	Write(STR, sizeof(STR)-1,ConsoleOutput);
	
//	Halt();
}
