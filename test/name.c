#include "syscall.h"
#define STR "Sahil"


int main()
{	
	/* write STR to data file */
	Write(STR, sizeof(STR)-1,ConsoleOutput);
	Exec("Halt");
}