// AddrSpace.java
//	Class to manage address spaces (executing user programs).
//
//	In order to run a user program, you must:
//
//	1. link with the -N -T 0 option 
//	2. run coff2noff to convert the object file to Nachos format
//		(Nachos object code format is essentially just a simpler
//		version of the UNIX executable object code format)
//	3. load the NOFF file into the Nachos file system
//		(if you haven't implemented the file system yet, you
//		don't need to do this last step)
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.userprog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.filesys.OpenFile;
import nachos.kernel.threads.Lock;
import nachos.kernel.threads.Semaphore;
import nachos.machine.CPU;
import nachos.machine.MIPS;
import nachos.machine.Machine;
import nachos.machine.TranslationEntry;
import nachos.noff.NoffHeader;

/**
 * This class manages "address spaces", which are the contexts in which user
 * programs execute. For now, an address space contains a "segment descriptor",
 * which describes the the virtual-to-physical address mapping that is to be
 * used when the user program is executing. As you implement more of Nachos, it
 * will probably be necessary to add other fields to this class to keep track of
 * things like open files, network connections, etc., in use by a user program.
 *
 * NOTE: Most of what is in currently this class assumes that just one user
 * program at a time will be executing. You will have to rewrite this code so
 * that it is suitable for multiprogramming.
 * 
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
public class AddrSpace {

    /** Page table that describes a virtual-to-physical address mapping. */
    public TranslationEntry pageTable[];

    /** Default size of the user stack area -- increase this as necessary! */
    private static final int UserStackSize = 1024;

    /**
     * A unique identifier for each address space
     */
    public int spaceID;

    /**
     * Semaphores that are used in join
     */
    public static Semaphore semaphore = new Semaphore("Syscall", 0);

    /**
     * This is the list of AddrSpaces that are associated with a AddrSpaces
     * Basically these are the ones that are waiting for this thread to finish
     */

    public List<AddrSpace> addrSpaces = new ArrayList<>();

    /** Map that keeps record of address pointers and Open file for Mmap. */
    public Map<Integer, OpenFile> openFileMap;

    /** Map that keeps record of virtual address pointers and starting index. */
    public Map<Integer, Integer> mapOfVirPNStartingIndex;

    /** Lock to hold the process. */
    public Lock lock;

    /**
     * Create a new address space.
     */
    public AddrSpace()

    {
	// allocate a space ID and register in the map
	spaceID = ++PhysicalMemoryManager.spaceID;
	PhysicalMemoryManager.mapOfAddrSpace.put(spaceID, this);

	// initialize the openFileMap
	openFileMap = new HashMap<>();
	lock = new Lock("Mmap Lock");

	mapOfVirPNStartingIndex = new HashMap();
    }

    /**
     * Load the program from a file "executable", and set everything up so that
     * we can start executing user instructions.
     *
     * Assumes that the object code file is in NOFF format.
     *
     * First, set up the translation from program memory to physical memory. For
     * now, this is really simple (1:1), since we are only uniprogramming.
     *
     * @param executable
     *            The file containing the object code to load into memory
     * @return -1 if an error occurs while reading the object file, otherwise 0.
     */
    public int exec(OpenFile executable) {
	NoffHeader noffH;
	long size;

	if ((noffH = NoffHeader.readHeader(executable)) == null)
	    return (-1);

	// how big is address space?
	size = roundToPage(noffH.code.size)
		+ roundToPage(noffH.initData.size + noffH.uninitData.size)
		+ UserStackSize; // we need to increase the size
				 // to leave room for the stack
	int numPages = (int) (size / Machine.PageSize);

	Debug.ASSERT((numPages <= Machine.NumPhysPages), // check we're not
							 // trying
		"AddrSpace constructor: Not enough memory!");
	// to run anything too big --
	// at least until we have
	// virtual memory

	Debug.println('a', "Initializing address space, numPages=" + numPages
		+ ", size=" + size);

	// first, set up the translation
	pageTable = new TranslationEntry[numPages];
	int location;
	for (int i = 0; i < numPages; i++) {
	    pageTable[i] = new TranslationEntry();
	    pageTable[i].virtualPage = i; // for now, virtual page# = phys page#

	    location = PhysicalMemoryManager.getInstance().getIndex();
	    pageTable[i].physicalPage = location;

	    pageTable[i].valid = true;
	    pageTable[i].use = false;
	    pageTable[i].dirty = false;
	    pageTable[i].readOnly = false; // if code and data segments live on
					   // separate pages, we could set code
					   // pages to be read-only
	}

	// Zero out the entire address space, to zero the uninitialized data
	// segment and the stack segment.

	// Added
	// Only free the index at which current physical page is stored
	for (int i = 0; i < pageTable.length; i++) {
	    Machine.mainMemory[pageTable[i].physicalPage
		    * Machine.PageSize] = (byte) 0;
	}

	// then, copy in the code and data segments into memory
	if (noffH.code.size > 0) {

	    // Now since our physical and virtual addresses are not same ,
	    // therefore we pass into executable.read()
	    // different arguments as compared to before

	    for (int i = 0; i < roundToPage(noffH.code.size)
		    / Machine.PageSize; i++) {

		executable.seek(noffH.code.inFileAddr + i * Machine.PageSize);
		executable.read(Machine.mainMemory,
			pageTable[i].physicalPage * Machine.PageSize,
			Machine.PageSize);
	    }
	}

	if (noffH.initData.size > 0) {
	    int startIndex = (int) (roundToPage(noffH.code.size)
		    / Machine.PageSize);

	    Debug.println('a', "Initializing data segment, at " + startIndex
		    + ", size " + Machine.PageSize);

	    for (int j = startIndex; j < roundToPage(noffH.initData.size)
		    / Machine.PageSize; j++) {

		executable
			.seek(noffH.initData.inFileAddr + j * Machine.PageSize);
		executable.read(Machine.mainMemory,
			pageTable[j].physicalPage * Machine.PageSize,
			Machine.PageSize);
	    }
	}

	return (0);
    }

    /**
     * Initialize the user-level register set to values appropriate for starting
     * execution of a user program loaded in this address space.
     *
     * We write these directly into the "machine" registers, so that we can
     * immediately jump to user code.
     */
    public void initRegisters() {
	int i;

	for (i = 0; i < MIPS.NumTotalRegs; i++)
	    CPU.writeRegister(i, 0);

	// Initial program counter -- must be location of "Start"
	CPU.writeRegister(MIPS.PCReg, 0);

	// Need to also tell MIPS where next instruction is, because
	// of branch delay possibility
	CPU.writeRegister(MIPS.NextPCReg, 4);

	// Set the stack register to the end of the segment.
	// NOTE: Nachos traditionally subtracted 16 bytes here,
	// but that turns out to be to accomodate compiler convention that
	// assumes space in the current frame to save four argument registers.
	// That code rightly belongs in start.s and has been moved there.
	int sp = pageTable.length * Machine.PageSize;
	CPU.writeRegister(MIPS.StackReg, sp);
	Debug.println('a', "Initializing stack register to " + sp);
    }

    /**
     * On a context switch, save any machine state, specific to this address
     * space, that needs saving.
     *
     * For now, nothing!
     */
    public void saveState() {
    }

    /**
     * On a context switch, restore any machine state specific to this address
     * space.
     *
     * For now, just tell the machine where to find the page table.
     */
    public void restoreState() {
	CPU.setPageTable(pageTable);
    }

    /**
     * Utility method for rounding up to a multiple of CPU.PageSize;
     */
    private long roundToPage(long size) {
	return (Machine.PageSize
		* ((size + (Machine.PageSize - 1)) / Machine.PageSize));
    }

    /**
     * Method called from Syscall.exit() to free space occupied by a particular
     * thread. It looks for the physical page number and sets the boolean flag
     * true at the
     * nachos.kernel.userprog.PhysicalMemoryManager.physicalMemoryArray making
     * the space free so that it can be used again by some other address space.
     */

    public void freeSpace() {
	Debug.println('+', "Entered freeSpace() method in AddrSpace()");
	// Free space from Singleton class --
	// /nachos_bit_bucket/nachos/kernel/userprog/PhysicalMemoryManager.java
	for (int i = 0; i < pageTable.length; i++) {
	    if (PhysicalMemoryManager.getInstance() != null
		    && pageTable[i] != null) {
		PhysicalMemoryManager.getInstance()
			.freeIndex(pageTable[i].physicalPage);

	    }
	}

	// free memory from Machine.mainMemory using page tables
	for (int i = 0; i < pageTable.length; i++) {
	    for (int offset = 0; offset < Machine.PageSize; offset++) {
		if (pageTable[i] != null) {
		    if ((pageTable[i].physicalPage * Machine.PageSize
			    + offset) > 0) {
			Machine.mainMemory[pageTable[i].physicalPage
				* Machine.PageSize + offset] = (byte) 0;
		    }
		}

	    }
	}
    }

    public AddrSpace fork() {
	AddrSpace addrSpace = new AddrSpace();
	AddrSpace currentThreadSpace = this;

	// copy members
	addrSpace.addrSpaces = currentThreadSpace.addrSpaces;
	addrSpace.pageTable = currentThreadSpace.pageTable;
	addrSpace.spaceID = currentThreadSpace.spaceID;
	addrSpace.semaphore = currentThreadSpace.semaphore;

	return addrSpace;
    }

    /**
     * Extends address space by n
     * 
     * @param n
     * @return
     */

    public int extendAddressSpace(int n) {
	// acquire lock to prevent others accessing it.
	lock.acquire();

	int oldSize = pageTable.length;
	TranslationEntry pageTableNew[] = new TranslationEntry[pageTable.length
		+ n];
	// copy all entries from page table as it is
	for (int i = 0; i < pageTable.length; i++) {
	    pageTableNew[i] = pageTable[i];
	}

	// initialize new objects
	for (int i = pageTable.length; i < pageTableNew.length; i++) {
	    pageTableNew[i] = new TranslationEntry();
	    // valid should be false by default
	    pageTableNew[i].valid = false;
	    pageTableNew[i].physicalPage = -1;
	}
	this.pageTable = pageTableNew;

	// update the page table
	CPU.setPageTable(pageTableNew);

	// release lock now
	lock.release();
	return oldSize * 128;
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
     * which pages are clean and which are dirty, clear the dirty bit in the
     * page table when you initially map the page. The MMU hardware will set the
     * dirty bit whenever a write access is made to the page. Before eventually
     * freeing the page, check the dirty bit and if it is set, write the
     * contents back to the file.
     * 
     * To complete this assignment, you will need to implement a page fault
     * handler, which gets dispatched out of the exception handler when a page
     * fault exception occurs. The page fault handler is responsible for
     * identifying the virtual page number on which the fault occurred,
     * determining whether the page in question is currently resident or not,
     * paging in data from the underlying file if the page is not resident,
     * modifying the page table for the faulting process, and and finally
     * returning to user mode to restart the faulting instruction. The page
     * fault handler is also responsible for identifying write accesses and
     * flagging dirty pages, as described above. Do not worry about page
     * replacement for this assignment; just return allocated pages to the free
     * memory pool when a process terminates or when it unmaps the mapped region
     * using Munmap().
     * 
     * You should construct some test applications that demonstrate the
     * functioning of your Mmap facility. You can do initial testing and
     * debugging using the stub filesystem, but then you should test your
     * implementation using the real filesystem. To set up to use the real
     * filesystem, you need to set the flags DISK and FILESYSTEM to true and
     * flag FILESYS_STUB to false in the Nachos class. By default, a file DISK
     * will be created to contain the disk image when the disk driver is
     * initialized. The first time you use the disk image, you need to format it
     * by passing the "-f" argument to NACHOS. Then, you need to write some
     * files to the newly formatted filesystem. For that, you can use the "-cp"
     * flag to NACHOS, which is handled by the FileSystemTest class.
     * 
     * @param address
     */
    public void executeMunmap(int virtualAddress) {
	OpenFile openFile = this.openFileMap.get(virtualAddress);

	// get virtual page number
	int virtualPageNumber = ((virtualAddress >> 7) & 0x1ffffff);

	// From this virtual page number till end of page table, free the memory

	int physicalMemoryIndex;
	for (int i = virtualPageNumber; i < pageTable.length; i++) {
	    physicalMemoryIndex = pageTable[i].physicalPage;
	    PhysicalMemoryManager.getInstance().freeIndex(physicalMemoryIndex);
	    pageTable[i].valid = false;

	    if (pageTable[i].dirty) {
		// copy byte buffer from main memory into open file at position
		// virtualPageNumber*128

		// get virtual page number and offset from virtual address
		int offset = (virtualAddress & 0x7f);
		int physicalPageNumber = pageTable[i].physicalPage;
		int physicalPageAddress = ((physicalPageNumber << 7) | offset);
		byte[] buffer = new byte[128];
		System.arraycopy(Machine.mainMemory, physicalPageAddress,
			buffer, 0, Machine.PageSize);
		// printBuffer(buffer);
		// found
		if (!mapOfVirPNStartingIndex.isEmpty()) {
		    int startingIndex = mapOfVirPNStartingIndex.get(i);
		    openFile.writeAt(buffer, 0, Machine.PageSize,
			    startingIndex);
		}
	    }
	}

	// shrink the page table array to a new pagetable array with
	// size=virtualPageNumber
	TranslationEntry[] pageTableNew = new TranslationEntry[virtualPageNumber];
	for (int i = 0; i < pageTableNew.length; i++) {
	    pageTableNew[i] = pageTable[i];
	}
	this.pageTable = pageTableNew;
    }

    /**
     * Prints the contents of buffer to be printed
     * 
     * @param buffer
     */
    private void printBuffer(byte[] buffer) {
	Debug.println('f', "Printing byte buffer");
	for (int i = 0; i < buffer.length; i++) {
	    Debug.println('f', String.valueOf((char) buffer[i]));
	}
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
     * @param sizePointer
     * @param fileSize
     */
    public int executeMmap(String fileName, int sizePointer) {
	int startingAddress = 0;
	OpenFile openFile = Nachos.fileSystem.open(fileName);
	if (openFile != null) {

	    // extend address space by n; n is the number of extra pages
	    long n = roundToPage(openFile.length()) / Machine.PageSize;
	    if (n < 1) {
		return 0;
	    }
	    startingAddress = extendAddressSpace((int) n);

	    // update sizePointer
	    byte[] buffer = new byte[4];
	    Nachos.fileSystem.intToBytes((int) openFile.length(), buffer, 0);

	    // reverse the buffer array
	    reverseBufferArray(buffer);

	    // write size pointer to main memory
	    writeSizePointerToMainMemory(sizePointer, buffer);

	    // update the map
	    openFileMap.put(startingAddress, openFile);
	}
	return startingAddress;
    }

    private void writeSizePointerToMainMemory(int virtualAddress,
	    byte[] buffer) {
	int virtualPageNumber = ((virtualAddress >> 7) & 0x1ffffff);
	// get virtual page number and offset from virtual address
	int offset = (virtualAddress & 0x7f);
	int physicalPageNumber = pageTable[virtualPageNumber].physicalPage;
	int physicalPageAddress = ((physicalPageNumber << 7) | offset);

	// copy bytes into main Memory
	for (int i = physicalPageAddress; i < physicalPageAddress + 4; i++) {
	    Machine.mainMemory[physicalPageAddress] = buffer[i
		    - physicalPageAddress];
	}
    }

    private void reverseBufferArray(byte[] buffer) {
	byte temp = buffer[0];
	buffer[0] = buffer[3];
	buffer[3] = temp;

	temp = buffer[1];
	buffer[1] = buffer[2];
	buffer[2] = temp;
    }

    /**
     * The method handles page fault exception.
     * 
     * @param virtualAddress
     */

    public void handlePageFaultException(int virtualAddress) {
	int virtualPageNumber = ((virtualAddress >> 7) & 0x1ffffff);
	int physicalPageNumber = PhysicalMemoryManager.getInstance().getIndex();

	// allocate memory for physical page and set valid =true
	pageTable[virtualPageNumber].physicalPage = physicalPageNumber;
	pageTable[virtualPageNumber].valid = true;

	OpenFile file = this.openFileMap.get(virtualAddress);

	// copy the file at one page into byte array
	byte[] into = new byte[Machine.PageSize];
	int startingIndex = file.getSeekPosition();
	file.read(into, 0, Machine.PageSize);
	mapOfVirPNStartingIndex.put(virtualPageNumber, startingIndex);

	int offset = (virtualAddress & 0x7f);
	int physicalPageAddress = ((physicalPageNumber << 7) | offset);

	// copy byte array into main memory
	System.arraycopy(into, 0, Machine.mainMemory, physicalPageAddress,
		Machine.PageSize);
    }
}
