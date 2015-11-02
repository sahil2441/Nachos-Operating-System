/* Basic test of Sleep() system call */

#include "syscall.h"
#define STR "Sahil"

int main()
{
	//start multiple exec and play ping pong
	Exec("exec4");
	Exec("exec5");
	Exec("exec6");
}
