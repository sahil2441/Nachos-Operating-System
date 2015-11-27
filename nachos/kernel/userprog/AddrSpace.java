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
import java.util.List;

import nachos.Debug;
import nachos.kernel.filesys.OpenFile;
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

    /**
     * Create a new address space.
     */
    public AddrSpace()

    {
	// allocate a space ID and register in the map
	spaceID = ++PhysicalMemoryManager.spaceID;
	PhysicalMemoryManager.mapOfAddrSpace.put(spaceID, this);

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
		    Machine.mainMemory[pageTable[i].physicalPage
			    * Machine.PageSize + offset] = (byte) 0;
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
	int oldSize = pageTable.length;
	TranslationEntry pageTableNew[] = new TranslationEntry[pageTable.length
		+ n];
	// copy all entries from page table as it is
	for (int i = 0; i < pageTable.length; i++) {
	    pageTableNew[i] = pageTable[i];
	}
	this.pageTable = pageTableNew;

	//
	CPU.writeRegister(5, pageTableNew.length);

	// TODO: return the address of the start of the newly added region
	// of address space
	return oldSize;
    }

    /**
     * Invalidate and delete the address or Remove the mapping associated with
     * the address.
     * 
     * @param address
     */
    public void executeMunmap(int address) {
	TranslationEntry translationEntry = pageTable[address];
	if (translationEntry != null) {
	    translationEntry.valid = false;
	    translationEntry.dirty = true;
	    int physicalPage = translationEntry.physicalPage;
	}

    }
}
