package nachos.kernel.userprog;

import nachos.Debug;
import nachos.kernel.threads.Lock;
import nachos.machine.Machine;

/**
 * This is unique instance of Physical memory manager class by using singleton
 * pattern.
 * 
 * @author sahiljain
 *
 */
public class PhysicalMemoryManager {

    /**
     * 
     */
    private static Lock pageLock = new Lock("PageLock");

    /**
     * Size of the physical memory is equal to the Number of physical pages in
     * Memory==Machine.NumPhysPages
     */
    public final int SIZE = Machine.NumPhysPages;

    /**
     * Physical memory
     */
    public boolean[] physicalMemoryArray = new boolean[SIZE];

    /**
     * current index of memory which has been written
     */
    public int index;

    /**
     * Unique Instance
     */
    private static PhysicalMemoryManager instance;

    /**
     * A private Constructor prevents any other class from instantiating.
     */
    private PhysicalMemoryManager() {
	index = 0;
    }

    /**
     * The Static initializer constructs the instance at class loading time;
     * this is to simulate a more involved construction process
     */
    static {
	instance = new PhysicalMemoryManager();
    }

    /** Static 'instance' method */
    public static PhysicalMemoryManager getInstance() {
	return instance;
    }

    public int getIndex() {
	PhysicalMemoryManager.pageLock.acquire();
	int i = 0;
	while (physicalMemoryArray[i] && i < Machine.NumPhysPages) {
	    i++;
	}
	if (i < Machine.NumPhysPages) {
	    physicalMemoryArray[i] = true;
	    PhysicalMemoryManager.pageLock.release();

	    return i;
	}
	// no free memory
	PhysicalMemoryManager.pageLock.release();
	return -1;
    }

    public void freeIndex(int index) {
	Debug.println('+',
		"Entered method nachos.kernel.userprog.PhysicalMemoryManager.freeIndex(int)"
			+ ", Freeing memory at index: " + index);
	physicalMemoryArray[index] = false;
    }
}
