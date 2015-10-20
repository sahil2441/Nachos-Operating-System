#include "syscall.h"
#define STR "Stony Brook University"


int main()
{	
	/* write STR to data file */
	Write(STR, sizeof(STR)-1,ConsoleOutput);
}