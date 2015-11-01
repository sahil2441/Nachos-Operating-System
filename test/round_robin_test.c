/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR "Sahil"

int main()
{
  int i, j;

	/* write STR to data file */
	 for( i=0;i<5;i++) {
		Write(STR, sizeof(STR),ConsoleOutput);
  }
}
