/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR_BEFORE "STR_BEFORE"
#define STR_AFTER "STR_AFTER"

int main()
{
	/* write STR to data file */
	Print();
	Sleep(1000);
	Print();
}
