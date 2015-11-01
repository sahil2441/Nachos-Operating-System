/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR "Sahil"

int main()
{
	//start two exec and play ping pong
	Exec("exec1");
	Exec("exec2");
	
}
