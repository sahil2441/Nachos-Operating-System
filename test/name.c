#include "syscall.h"
#define STR "Jingle Bells Jingle Bells Jingle all the way\n"


int main()
{	
	/* write STR to data file */
	Write(STR, sizeof(STR)-1,ConsoleOutput);
}