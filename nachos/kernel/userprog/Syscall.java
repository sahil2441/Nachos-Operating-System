// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.userprog;

import java.util.List;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.devices.ConsoleDriver;
import nachos.kernel.filesys.OpenFile;
import nachos.kernel.threads.Semaphore;
import nachos.machine.CPU;
import nachos.machine.MIPS;
import nachos.machine.Machine;
import nachos.machine.NachosThread;
import nachos.machine.Simulation;

/**
 * Nachos system call interface. These are Nachos kernel operations that can be
 * invoked from user programs, by trapping to the kernel via the "syscall"
 * instruction.
 *
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
public class Syscall {

    public OpenFile file;

    // System call codes -- used by the stubs to tell the kernel
    // which system call is being asked for.

    /** Integer code identifying the "Halt" system call. */
    public static final byte SC_Halt = 0;

    /** Integer code identifying the "Exit" system call. */
    public static final byte SC_Exit = 1;

    /** Integer code identifying the "Exec" system call. */
    public static final byte SC_Exec = 2;

    /** Integer code identifying the "Join" system call. */
    public static final byte SC_Join = 3;

    /** Integer code identifying the "Create" system call. */
    public static final byte SC_Create = 4;

    /** Integer code identifying the "Open" system call. */
    public static final byte SC_Open = 5;

    /** Integer code identifying the "Read" system call. */
    public static final byte SC_Read = 6;

    /** Integer code identifying the "Write" system call. */
    public static final byte SC_Write = 7;

    /** Integer code identifying the "Close" system call. */
    public static final byte SC_Close = 8;

    /** Integer code identifying the "Fork" system call. */
    public static final byte SC_Fork = 9;

    /** Integer code identifying the "Yield" system call. */
    public static final byte SC_Yield = 10;

    /** Integer code identifying the "Remove" system call. */
    public static final byte SC_Remove = 11;

    /** Integer code identifying the "Sleep" system call. */
    public static final byte SC_Sleep = 12;

    /** Integer code identifying the "Print" system call. */
    public static final byte SC_Print = 13;

    /** Integer code identifying the "Make Directory" system call. */
    public static final byte SC_Mkdir = 14;

    /** Integer code identifying the "Remove Directory" system call. */
    public static final byte SC_Rmdir = 15;

    /** Integer code identifying the "Mmap" system call. */
    public static final byte SC_Mmap = 16;

    /** Integer code identifying the "Munmap" system call. */
    public static final byte SC_Munmap = 17;

    /**
     * Global variable for Process ID
     */
    public static int processID = 8000;

    /** Reference to the console device driver. */
    private static ConsoleDriver console;

    /**
     * Stop Nachos, and print out performance stats.
     */
    public static void halt() {
	Debug.print('+', "Shutdown, initiated by user program.\n");
	Simulation.stop();
    }

    /* Address space control operations: Exit, Exec, and Join */

    /**
     * This user program is done.
     *
     * @param status
     *            Status code to pass to processes doing a Join(). status = 0
     *            means the program exited normally.
     */
    public static void exit(int status) {
	Debug.println('+',
		"User program exits with status=" + status
			+ ", Current Thread Name: "
			+ NachosThread.currentThread().name);

	// free the memory space occupied by this thread
	AddrSpace space = ((UserThread) NachosThread.currentThread()).space;
	Debug.println('+',
		"Calling nachos.kernel.userprog.AddrSpace.freeSpace() from nachos.kernel.userprog.Syscall.exit(int) to free up the memory");
	space.freeSpace();

	// update the exit status in the map
	PhysicalMemoryManager.mapExitStatuses.put(
		((UserThread) NachosThread.currentThread()).space.spaceID,
		status);

	// Iterate through list of address spaces maintained in the class
	// AddrSpace and call semaphore.V() on each of them
	// This implies that all those threads that were waiting for this to
	// finish will now start execution
	List<AddrSpace> list = ((UserThread) NachosThread
		.currentThread()).space.addrSpaces;

	Debug.println('+',
		"Entered into nachos.kernel.userprog.Syscall.exit(int) , Calling semaphore.V() on all associated address spaces.");

	for (int i = 0; i < list.size(); i++) {
	    Debug.println('+',
		    "Entered into nachos.kernel.userprog.Syscall.exit(int) , Calling semaphore.V() into the loop");
	    list.get(i).semaphore.V();
	}
	Nachos.scheduler.finishThread();
    }

    /**
     * Run the executable, stored in the Nachos file "name", and return the
     * address space identifier.
     *
     * @param name
     *            The name of the file to execute.
     */
    public static int exec(String name) {

	Debug.println('+', "starting Exec from Syscall.java: ");
	AddrSpace space = ((UserThread) NachosThread.currentThread()).space;

	UserThread t = new UserThread(name, new Runnable() {

	    @Override
	    public void run() {
		Debug.println('+', "starting run() in exec() method "
			+ "from Syscall.java: ");

		// Executing c file here
		OpenFile executable;

		if ((executable = Nachos.fileSystem.open(name)) == null) {
		    Debug.println('+',
			    "Unable to open executable file: " + name);
		    Nachos.scheduler.finishThread();
		    return;
		}

		AddrSpace space = ((UserThread) NachosThread
			.currentThread()).space;
		Debug.println('+', "Calling space.exec() with executable file: "
			+ executable.toString()
			+ " from nachos.kernel.userprog.Syscall.exec(...).new Runnable() {...}.run()");
		if (space.exec(executable) == -1) {
		    Debug.println('+',
			    "Unable to read executable file: " + name);
		    Nachos.scheduler.finishThread();
		    return;
		}
		Debug.println('+', "Calling space.initRegisters(),"
			+ " from nachos.kernel.userprog.Syscall.exec(...).new Runnable() {...}.run()");

		space.initRegisters(); // set the initial register values

		Debug.println('+', "Calling space.restoreState(),"
			+ " from nachos.kernel.userprog.Syscall.exec(...).new Runnable() {...}.run()");

		space.restoreState(); // load page table register

		Debug.println('+',
			"Now Starting CPU.runUserCode() from: nachos.kernel.userprog.Syscall.exec(...).new Runnable() {...}.run()");

		CPU.runUserCode(); // jump to the user program
		Debug.ASSERT(false); // machine->Run never returns;
		// the address space exits
		// by doing the syscall "exit"
	    }
	}, space);
	Nachos.scheduler.readyToRun(t);

	return ((UserThread) NachosThread.currentThread()).space.spaceID;
    }

    /**
     * Wait for the user program specified by "id" to finish, and return its
     * exit status.
     *
     * @param id
     *            The "space ID" of the program to wait for.
     * @return the exit status of the specified program.
     */
    public static int join(int id) {
	Debug.println('+',
		"Executing nachos.kernel.userprog.Syscall.join(int) ");
	Debug.println('+', "Process ID recieved in join method: " + id);

	PhysicalMemoryManager.getInstance();
	// Retrieve the AddrSpace corresponding to the given id. Then in that
	// AddrSpace
	// add this address space to the list of Address spaces that is
	// maintained in the retrieved address space

	Debug.println('+', "Inside: nachos.kernel.userprog.Syscall.join(int), "
		+ "Adding this instance of address space to the list of address spaces");

	if (PhysicalMemoryManager.mapOfAddrSpace != null
		&& PhysicalMemoryManager.mapOfAddrSpace.get(id) != null) {
	    PhysicalMemoryManager.mapOfAddrSpace.get(id).addrSpaces
		    .add(((UserThread) (NachosThread.currentThread())).space);
	}

	Debug.println('+', "Inside: nachos.kernel.userprog.Syscall.join(int), "
		+ "Calling semaphore.P() in join method");
	((UserThread) (NachosThread.currentThread())).space.semaphore.P();

	Debug.println('+', "Inside: nachos.kernel.userprog.Syscall.join(int), "
		+ "Parent thread called semaphore.V()");
	Debug.println('+', "Inside: nachos.kernel.userprog.Syscall.join(int), "
		+ "Exit status returned is: "
		+ PhysicalMemoryManager.getInstance().mapExitStatuses.get(id));

	return PhysicalMemoryManager.getInstance().mapExitStatuses.get(id);
    }

    /*
     * File system operations: Create, Open, Read, Write, Close These functions
     * are patterned after UNIX -- files represent both files *and* hardware I/O
     * devices.
     *
     * If this assignment is done before doing the file system assignment, note
     * that the Nachos file system has a stub implementation, which will work
     * for the purposes of testing out these routines.
     */

    // When an address space starts up, it has two open files, representing
    // keyboard input and display output (in UNIX terms, stdin and stdout).
    // Read and write can be used directly on these, without first opening
    // the console device.

    /** OpenFileId used for input from the keyboard. */
    public static final int ConsoleInput = 0;

    /** OpenFileId used for output to the display. */
    public static final int ConsoleOutput = 1;

    /**
     * Create a Nachos file with a specified name.
     *
     * @param name
     *            The name of the file to be created.
     */
    public static void create(String name) {
    }

    /**
     * Remove a Nachos file.
     *
     * @param name
     *            The name of the file to be removed.
     */
    public static void remove(String name) {
    }

    /**
     * Open the Nachos file "name", and return an "OpenFileId" that can be used
     * to read and write to the file.
     *
     * @param name
     *            The name of the file to open.
     * @return An OpenFileId that uniquely identifies the opened file.
     */
    public static int open(String name) {
	return 0;
    }

    /**
     * Write "size" bytes from "buffer" to the open file.
     *
     * @param buffer
     *            Location of the data to be written.
     * @param size
     *            The number of bytes to write.
     * @param id
     *            The OpenFileId of the file to which to write the data.
     */
    public static void write(byte buffer[], int size, int id) {
	if (id == ConsoleOutput) {
	    for (int i = 0; i < size; i++) {
		System.out.println((char) buffer[i]);
		Nachos.consoleDriver.putChar((char) buffer[i]);
	    }
	}
    }

    /**
     * Read "size" bytes from the open file into "buffer". Return the number of
     * bytes actually read -- if the open file isn't long enough, or if it is an
     * I/O device, and there aren't enough characters to read, return whatever
     * is available (for I/O devices, you should always wait until you can
     * return at least one character).
     *
     * @param buffer
     *            Where to put the data read.
     * @param size
     *            The number of bytes requested.
     * @param id
     *            The OpenFileId of the file from which to read the data.
     * @return The actual number of bytes read.
     */
    public static int read(byte buffer[], int size, int id) {
	Debug.println('+',
		"ConsoleTest: starting. Entered method nachos.kernel.userprog.Syscall.read(byte[], int, int)");

	console = Nachos.consoleDriver;
	char[] outputBuffer = console.prepareOutputBufferForReadSysCall(size);

	// Print the output buffer to console
	console.printToConsole('\n');
	console.printOuputBufferToConsole(outputBuffer);

	// save buffer to main memory
	saveToMainMemory(outputBuffer);
	return 0;
    }

    private static void saveToMainMemory(char[] outputBuffer) {
	int virtualAddress = CPU.readRegister(4);
	int virtualPageNumber = ((virtualAddress >> 7) & 0x1ffffff);
	int offset = (virtualAddress & 0x7f);

	// get virtual page number and offset from virtual address
	AddrSpace space = ((UserThread) NachosThread.currentThread()).space;
	int physicalPageNumber = space.pageTable[virtualPageNumber].physicalPage;
	int physicalPageAddress = ((physicalPageNumber << 7) | offset);

	for (int i = 0; i < outputBuffer.length; i++) {
	    Machine.mainMemory[physicalPageAddress] = (byte) outputBuffer[i];
	    physicalPageAddress++;
	}
    }

    /**
     * Close the file, we're done reading and writing to it.
     *
     * @param id
     *            The OpenFileId of the file to be closed.
     */
    public static void close(int id) {
    }

    /*
     * User-level thread operations: Fork and Yield. To allow multiple threads
     * to run within a user program.
     */

    /**
     * Fork a thread to run a procedure ("func") in the *same* address space as
     * the current thread.
     *
     * @param func
     *            The user address of the procedure to be run by the new thread.
     */
    public static void fork(int func) {
	Debug.println('+',
		"Entered into nachos.kernel.userprog.Syscall.fork(int)");

	// create copy of the address space
	AddrSpace addrSpace = ((UserThread) NachosThread.currentThread()).space
		.fork();

	UserThread t = new UserThread("ForkThread", new Runnable() {

	    @Override
	    public void run() {
		Debug.println('+', "starting run() in fork() method "
			+ "from Syscall.java: ");

		AddrSpace space = ((UserThread) NachosThread
			.currentThread()).space;

		Debug.println('+', "Calling space.initRegisters(),"
			+ " from nachos.kernel.userprog.Syscall.fork(...).new Runnable() {...}.run()");

		space.initRegisters(); // set the initial register values

		Debug.println('+', "Calling space.restoreState(),"
			+ " from nachos.kernel.userprog.Syscall.fork(...).new Runnable() {...}.run()");

		space.restoreState(); // load page table register

		Debug.println('+',
			"Now Starting CPU.runUserCode() from: nachos.kernel.userprog.Syscall.fork(...).new Runnable() {...}.run()");

		CPU.runUserCode(); // jump to the user progam
		Debug.ASSERT(false); // machine->Run never returns;
		// the address space exits
		// by doing the syscall "exit"
	    }
	}, addrSpace);
	t.ptr = func;

	// Update the program counter to point to the next instruction
	// after the SYSCALL instruction.
	CPU.writeRegister(MIPS.PrevPCReg, CPU.readRegister(MIPS.PCReg));
	CPU.writeRegister(MIPS.PCReg, CPU.readRegister(MIPS.NextPCReg));
	CPU.writeRegister(MIPS.NextPCReg, func + 4);

	Nachos.scheduler.readyToRun(t);
    }

    /**
     * Yield the CPU to another runnable thread, whether in this address space
     * or not.
     */
    public static void yield() {
	Debug.println('+', "starting Yield() from Syscall.java: ");
	Nachos.scheduler.yieldThread();

    }

    /**
     * The idea is to add the current thread in a queue of sleeping threads,
     * defined in scheduler, and call samaphore.P() on this thread.
     * Semaphore.V() on this thread is called when number of ticks is exceeded--
     * this implementation is defined in
     * nachos.kernel.threads.Scheduler.TimerInterruptHandler.handleInterrupt()
     * 
     * @param sleepingTime
     */

    public static void sleep(int sleepingTime) {
	UserThread userThread = ((UserThread) NachosThread.currentThread());

	// check if this thread is owned by any CPU. If yes disown it and update
	// the map accordingly-- putting current thread on current CPU.
	for (int i = 0; i < Machine.NUM_CPUS; i++) {
	    CPU cpu = Machine.getCPU(i);
	    if (Nachos.scheduler.cpuThreadMap.get(cpu) == userThread) {
		Nachos.scheduler.cpuThreadMap.put(cpu, null);
		break;
	    }
	}
	userThread.noOfTicksRemainingForSleep = sleepingTime;
	Nachos.scheduler.getSleepThreadList().add(userThread);

	userThread.semaphore = new Semaphore(
		userThread.name + "-Semaphore for Sleep", 0);
	userThread.semaphore.P();
    }

    /**
     * Print system call Prints to console -- for debugging purposes
     */
    public static void print() {
	System.out.println(NachosThread.currentThread().name);
    }

    /**
     * Creates a directory with the name as directoryName
     * 
     * @param directoryName
     */

    public static void makeDirectory(String directoryName) {
	if (Nachos.fileSystem.createDirectory(directoryName)) {
	    Debug.println('f', "Created new directory with Mkdir Command at: "
		    + directoryName);
	} else {
	    Debug.println('f',
		    "Couldn't create a new directory with Mkdir Command at: "
			    + directoryName);
	}
	Nachos.fileSystem.list();
    }

    public static void removeDirectory(String directoryName) {
	if (Nachos.fileSystem.removeDirectory(directoryName)) {
	    Debug.println('f', "Removed a directory with Rmdir Command at: "
		    + directoryName);
	} else {
	    Debug.println('f',
		    "Couldn't remove the directory with Rmdir Command at: "
			    + directoryName);
	}
	Nachos.fileSystem.list();
    }

    /**
     * The Mmap system call takes as arguments a string name and an integer
     * pointer sizep. If name names an existing file, then the address space of
     * the calling process is extended at the high end (above the stack) by a
     * number of pages N such that N times Machine.PageSize is at least as great
     * as the length of the specified file. The page table entries that map the
     * newly added portion of the address space should be set initially to
     * "invalid", and no physical memory pages should initially be allocated for
     * these entries. In addition, the file should be opened and a reference to
     * the OpenFile object left associated with the process. A successful call
     * to Mmap should return the address of the start of the newly added region
     * of address space. In addition, the variable pointed to by sizep is
     * updated with the size of the newly allocated region of address space. An
     * unsuccessful call to Mmap should return 0 and store no value at sizep.
     * 
     * After a successful call to Mmap, when the process attempts to access the
     * newly added region of address space, a page fault should occur, and the
     * page fault handler should handle the fault by allocating a new physical
     * page, reading in the corresponding data from the OpenFile, updating the
     * page table with a new virtual-to-physical mapping, and returning to user
     * mode to retry the instruction that caused the fault. The effect should be
     * as if the contents of the memory-mapped file actually appeared in the
     * process' address space in the newly allocated region. It is permissible
     * for a process to make several calls to Mmap. In this case, each
     * successive call should make an additional extension to the address space,
     * with each new extension starting just above the preceding one.
     * 
     * @param fileName
     * @param fileSize
     */
    // TODO
    public static int mmap(String fileName, int fileSize) {
	if (Nachos.fileSystem.checkIfFileExists(fileName)) {
	    AddrSpace space = ((UserThread) NachosThread.currentThread()).space;
	    // extend address space by n
	    int fileLength = Nachos.fileSystem.getFileLength(fileName);
	    int n = (int) Math.ceil(fileLength / Machine.PageSize);
	    if (n < 1)
		return -1;

	    // open the file
	    // TODO: Leave the reference
	    OpenFile file = Nachos.fileSystem.open(fileName);

	    return space.extendAddressSpace(n);
	}
	return 0;
    }

    /**
     * The Munmap call takes as its argument an address that was returned by a
     * previous call to Mmap, and it should cause the mapping of the
     * corresponding region of address space to be invalidated and deleted. Any
     * memory pages associated with this region should be returned to the free
     * memory pool.
     * 
     * A process is permitted to write into a memory-mapped region of its
     * address space. In this case, the page that was written should be marked
     * as "dirty" and arrangements should be made for it to be written back to
     * the disk at the time the file is unmapped. Pages that have not been
     * written are "clean" and should not be written back to the disk. To track
     * which pages are clean and which are dirty, initially set all pages in
     * memory-mapped regions to invalid and read-only. The first time a page
     * fault occurs, assume it is a read access and set the corresponding page
     * table entry to valid and read-only. If a page fault occurs on a page
     * whose page table entry is marked valid and read-only, you will know that
     * it is because the process is performing a write access. In that case set
     * the page table entry to read/write and flag the page as dirty.
     * 
     * @param address
     */
    public static void Munmap(int address) {
	// TODO Auto-generated method stub
	AddrSpace space = ((UserThread) NachosThread.currentThread()).space;
	space.executeMunmap(address);

    }
}
