
#include "syscall.h"
#define STR "Write Syscall"

int main()
{
	int pid=Exec("test/name");	 
//	Exec("test/university");
//	Yield();

	Join(pid);
	
	/* write STR to data file */
//	Write(STR, sizeof(STR)-1,ConsoleOutput);

	Exec("test/console1");

}