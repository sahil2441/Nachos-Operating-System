/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR_BEFORE "STR_BEFORE"
#define STR_AFTER "STR_AFTER"

int main()
{
	/* write STR to data file */
	Write(STR_BEFORE, sizeof(STR_BEFORE)-1,ConsoleOutput);
	Sleep(100000);
	Write(STR_AFTER, sizeof(STR_AFTER)-1,ConsoleOutput);
}
