package nachos.kernel.userprog;

import java.util.HashMap;
import java.util.Map;

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
     * This lock ensures that only one process can access this singleton class.
     */
    private static Lock pageLock = new Lock("PageLock");

    /**
     * Space ID used by Address Space object. Each Address space has a unique
     * space ID.
     */
    public static int spaceID = 8000;

    /**
     * This map maintains a relation between process ID and AddrSpace
     */
    public static Map<Integer, AddrSpace> mapOfAddrSpace = new HashMap();

    /**
     * This map maintains the information about processID that which process has
     * stopped executing.
     */
    public static Map<Integer, Integer> mapExitStatuses = new HashMap();

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
	Debug.println('+',
		"Inside method: nachos.kernel.userprog.PhysicalMemoryManager.getIndex()");
	// PhysicalMemoryManager.pageLock.acquire();
	int i = 0;
	while (physicalMemoryArray[i] && i < Machine.NumPhysPages) {
	    i++;
	}
	if (i < Machine.NumPhysPages) {
	    physicalMemoryArray[i] = true;
	    // PhysicalMemoryManager.pageLock.release();

	    return i;
	}
	// no free memory
	Debug.println('+', "No free memory right now. Returning -1.");
	// PhysicalMemoryManager.pageLock.release();
	return -1;
    }

    public void freeIndex(int index) {
	if (index != -1) {
	    Debug.println('+',
		    "Entered method nachos.kernel.userprog.PhysicalMemoryManager.freeIndex(int)"
			    + ", Freeing memory at index: " + index);
	    physicalMemoryArray[index] = false;
	}
    }
}
