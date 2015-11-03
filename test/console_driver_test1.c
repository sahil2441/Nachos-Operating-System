/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR1 "StonyBrookUniversity"
#define STR2 "Operating"
#define STR3 "Systems"


int main()
{	
	/* write STR to data file */
	Write(STR1, sizeof(STR1)-1,ConsoleOutput);
	Write(STR2, sizeof(STR2)-1,ConsoleOutput);
	Write(STR3, sizeof(STR3)-1,ConsoleOutput);
}
