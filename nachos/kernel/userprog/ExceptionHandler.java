// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.userprog;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.machine.CPU;
import nachos.machine.MIPS;
import nachos.machine.Machine;
import nachos.machine.MachineException;
import nachos.machine.NachosThread;

/**
 * An ExceptionHandler object provides an entry point to the operating system
 * kernel, which can be called by the machine when an exception occurs during
 * execution in user mode. Examples of such exceptions are system call
 * exceptions, in which the user program requests service from the OS, and page
 * fault exceptions, which occur when the user program attempts to access a
 * portion of its address space that currently has no valid virtual-to-physical
 * address mapping defined. The operating system must register an exception
 * handler with the machine before attempting to execute programs in user mode.
 */
public class ExceptionHandler implements nachos.machine.ExceptionHandler {

    /**
     * Entry point into the Nachos kernel. Called when a user program is
     * executing, and either does a syscall, or generates an addressing or
     * arithmetic exception.
     *
     * For system calls, the following is the calling convention:
     *
     * system call code -- r2, arg1 -- r4, arg2 -- r5, arg3 -- r6, arg4 -- r7.
     *
     * The result of the system call, if any, must be put back into r2.
     *
     * And don't forget to increment the pc before returning. (Or else you'll
     * loop making the same system call forever!)
     *
     * @param which
     *            The kind of exception. The list of possible exceptions is in
     *            CPU.java.
     *
     * @author Thomas Anderson (UC Berkeley), original C++ version
     * @author Peter Druschel (Rice University), Java translation
     * @author Eugene W. Stark (Stony Brook University)
     */
    public void handleException(int which) {
	int processID;
	int type = CPU.readRegister(2);

	int virtualAddress, virtualPageNumber, physicalPageAddress, index,
		physicalPageNumber, len, pid, startIndex;
	AddrSpace space;
	byte[] buf;
	String directoryName;

	if (which == MachineException.SyscallException) {
	    Debug.println('+',
		    "Entered into nachos.kernel.userprog.ExceptionHandler.handleException(int)"
			    + " with 'which' : " + which + " and 'type: '"
			    + type);

	    int offset;
	    switch (type) {

	    case Syscall.SC_Halt:
		Debug.println('+', "Syscall is : Syscall.Halt");
		Syscall.halt();
		break;

	    case Syscall.SC_Fork:
		Debug.println('+', "Syscall is : Syscall.SC_Fork");
		int pointerToFunction = CPU.readRegister(4);
		Syscall.fork(pointerToFunction);
		break;

	    case Syscall.SC_Exec:
		Debug.println('+', "Syscall is : Syscall.SC_Exec");
		startIndex = CPU.readRegister(4);
		String executableFile = obtainExecutableFileName(startIndex);
		processID = Syscall.exec("test/" + executableFile);
		Debug.println('+', "Proces ID after executing Syscall.Exec(): "
			+ processID);
		CPU.writeRegister(2, processID);
		break;

	    case Syscall.SC_Exit:
		Debug.println('+', "Syscall is : Syscall.SC_Exit");
		Syscall.exit(CPU.readRegister(4));
		break;

	    case Syscall.SC_Join:
		pid = CPU.readRegister(4);
		Debug.println('+', "Syscall is : Syscall.SC_Join");
		Syscall.join(pid);
		break;

	    case Syscall.SC_Sleep:
		int sleepingTime = CPU.readRegister(4);
		Syscall.sleep(sleepingTime);
		Debug.println('+', "Syscall is : Syscall.SC_Sleep");
		break;

	    case Syscall.SC_Print:
		Syscall.print();
		break;
	    case Syscall.SC_Yield:
		Syscall.yield();
		break;

	    case Syscall.SC_Read:
		Debug.println('+', "Syscall is : Syscall.SC_Read");
		// This is the machine address where we are required to save our
		// input text that we get from console
		len = CPU.readRegister(5);
		buf = new byte[len];
		// prepare buf array to read from Console and then save it to
		// Machine.mainMemory

		Syscall.read(buf, len, CPU.readRegister(6));
		break;

	    case Syscall.SC_Write:
		// First of all we need to get physical page address from
		// virtual page address and then copy the data from the
		// machine.mainMemory into buffer array
		// we will keep copying the data till the index i=0 reaches
		// i=len-1
		// reaches. We'll also increment offset value and check if it
		// reaches
		// 127 before index i reaches len-1
		// In that case we need to copy data from next page -- by
		// finding
		// next physical page address from virtual page address

		// So in short in write we copy data from machine.mainMemory to
		// buff array and print this data to console

		Debug.println('+', "Syscall is : Syscall.SC_Write");
		virtualAddress = CPU.readRegister(4);
		virtualPageNumber = ((virtualAddress >> 7) & 0x1ffffff);
		// get virtual page number and offset from virtual address
		offset = (virtualAddress & 0x7f);

		len = CPU.readRegister(5);
		buf = new byte[len];

		// index that ensures that all data has been copied successfully
		// It must reach len
		index = 0;

		while (index < len) {

		    // get physical page number from virtual page number
		    space = ((UserThread) NachosThread.currentThread()).space;
		    physicalPageNumber = space.pageTable[virtualPageNumber].physicalPage;
		    physicalPageAddress = ((physicalPageNumber << 7) | offset);

		    while (offset < Machine.PageSize && index < len) {
			buf[index] = Machine.mainMemory[physicalPageAddress];
			index++;
			offset++;
			physicalPageAddress++;
		    }
		    virtualPageNumber++;
		    offset = 0;
		}

		Syscall.write(buf, len, CPU.readRegister(6));
		break;

	    case Syscall.SC_Remove:
		Syscall.remove("");
		break;

	    case Syscall.SC_Mkdir:
		Debug.println('+', "Syscall is : Syscall.SC_Mkdir");
		startIndex = CPU.readRegister(4);
		directoryName = obtainExecutableFileName(startIndex);
		Syscall.makeDirectory(directoryName);
		break;

	    case Syscall.SC_Rmdir:
		Debug.println('+', "Syscall is : Syscall.SC_Rmdir");
		startIndex = CPU.readRegister(4);
		directoryName = obtainExecutableFileName(startIndex);
		Syscall.removeDirectory(directoryName);
		break;

	    case Syscall.SC_Mmap:
		Debug.println('+', "Syscall is : Syscall.SC_Mmap");
		startIndex = CPU.readRegister(4);
		String fileName = obtainExecutableFileName(startIndex);

		int sizePointer = CPU.readRegister(5);
		int result = Syscall.mmap(fileName, sizePointer);

		// Writing the address of the new created block into register 2
		// which will be used by syscall Munmap
		Debug.println('f', "Mmap passing address: " + result);
		CPU.writeRegister(2, result);
		break;

	    case Syscall.SC_Munmap:
		Debug.println('+', "Syscall is : Syscall.SC_Munmap");

		// get the argument passed to Mummap at register 4
		int address = CPU.readRegister(4);
		Debug.println('f', "Address passed to Munmap: " + address);
		int status = Syscall.Munmap(address);
		CPU.writeRegister(2, status);
		break;
	    }

	    // Update the program counter to point to the next instruction
	    // after the SYSCALL instruction.
	    CPU.writeRegister(MIPS.PrevPCReg, CPU.readRegister(MIPS.PCReg));
	    CPU.writeRegister(MIPS.PCReg, CPU.readRegister(MIPS.NextPCReg));
	    CPU.writeRegister(MIPS.NextPCReg,
		    CPU.readRegister(MIPS.NextPCReg) + 4);

	    return;
	}

	else if (which == MachineException.PageFaultException) {
	    Debug.println('+', "which=2 and PageFaultException occured.");

	    // get virtual address
	    virtualAddress = CPU.readRegister(MIPS.BadVAddrReg);
	    AddrSpace addrssSpace = ((UserThread) NachosThread
		    .currentThread()).space;
	    addrssSpace.handlePageFaultException(virtualAddress);

	    return;

	}

	// System.out.println(
	// "Unexpected user mode exception " + which + ", " + type);
	// Debug.ASSERT(false);

	// Finish thread here
	Nachos.scheduler.finishThread();

    }

    private String obtainExecutableFileName(int ptr) {
	StringBuilder executable = new StringBuilder("");
	byte buffer[] = Machine.mainMemory;
	while (buffer[ptr] != 0) {
	    executable.append((char) buffer[ptr]);
	    ptr++;
	}
	return executable.toString();
    }
}
