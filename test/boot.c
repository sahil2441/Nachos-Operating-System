
#include "syscall.h"
#define STR "Write Syscall"

void func(){

	/* write STR to data file */
	Write(STR, sizeof(STR)-1,ConsoleOutput);
}

int main(){

		Fork(func);
	}

