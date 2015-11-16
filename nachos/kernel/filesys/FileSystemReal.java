// FileSystemReal.java
//	Class to manage the overall operation of the file system.
//	Implements methods to map from textual file names to files.
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.filesys;

import java.util.HashMap;
import java.util.Map;

import nachos.Debug;
import nachos.kernel.devices.DiskDriver;

/**
 * This class manages the overall operation of the file system. It implements
 * methods to map from textual file names to files. Each file in the file system
 * has: A file header, stored in a sector on disk (the size of the file header
 * data structure is arranged to be precisely the size of 1 disk sector); A
 * number of data blocks; An entry in the file system directory.
 *
 * The file system consists of several data structures: A bitmap of free disk
 * sectors (cf. bitmap.h); A directory of file names and file headers.
 *
 * Both the bitmap and the directory are represented as normal files. Their file
 * headers are located in specific sectors (sector 0 and sector 1), so that the
 * file system can find them on bootup.
 *
 * The file system assumes that the bitmap and directory files are kept "open"
 * continuously while Nachos is running.
 *
 * For those operations (such as create, remove) that modify the directory
 * and/or bitmap, if the operation succeeds, the changes are written immediately
 * back to disk (the two files are kept open during all this time). If the
 * operation fails, and we have modified part of the directory and/or bitmap, we
 * simply discard the changed version, without writing it back to disk.
 *
 * Our implementation at this point has the following restrictions:
 *
 * there is no synchronization for concurrent accesses; files have a fixed size,
 * set when the file is created; files cannot be bigger than about 3KB in size;
 * there is no hierarchical directory structure, and only a limited number of
 * files can be added to the system; there is no attempt to make the system
 * robust to failures (if Nachos exits in the middle of an operation that
 * modifies the file system, it may corrupt the disk).
 *
 * A file system is a set of files stored on disk, organized into directories.
 * Operations on the file system have to do with "naming" -- creating, opening,
 * and deleting files, given a textual file name. Operations on an individual
 * "open" file (read, write, close) are to be found in the OpenFile class
 * (OpenFile.java).
 *
 * We define two separate implementations of the file system. This version is a
 * "real" file system, built on top of a disk simulator. The disk is simulated
 * using the native file system on the host platform (in a file named "DISK").
 *
 * In the "real" implementation, there are two key data structures used in the
 * file system. There is a single "root" directory, listing all of the files in
 * the file system; unlike UNIX, the baseline system does not provide a
 * hierarchical directory structure. In addition, there is a bitmap for
 * allocating disk sectors. Both the root directory and the bitmap are
 * themselves stored as files in the Nachos file system -- this causes an
 * interesting bootstrap problem when the simulated disk is initialized.
 *
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
class FileSystemReal extends FileSystem {

    // Sectors containing the file headers for the bitmap of free sectors,
    // and the directory of files. These file headers are placed in
    // well-known sectors, so that they can be located on boot-up.

    /** The disk sector containing the bitmap of free sectors. */
    private static final int FreeMapSector = 0;

    /** The disk sector containing the directory of files. */
    private static final int DirectorySector = 1;

    /** The maximum number of entries in a directory. */
    private static final int NumDirEntries = 10;

    /** Access to the disk on which the filesystem resides. */
    private final DiskDriver diskDriver;

    /** Number of sectors on the disk. */
    public final int numDiskSectors;

    /** Sector size of the disk. */
    public final int diskSectorSize;

    // Initial file sizes for the bitmap and directory; until the file system
    // supports extensible files, the directory size sets the maximum number
    // of files that can be loaded onto the disk.

    /** The initial file size for the bitmap file. */
    private final int FreeMapFileSize;

    /** The initial size of a directory file. */
    private final int DirectoryFileSize;

    /** Bit map of free disk blocks, represented as a file. */
    private final OpenFile freeMapFile;

    /** "Root" directory -- list of file names, represented as a file. */
    private final OpenFile directoryFile;

    /**
     * Map of Directories. It's to check which directories we have in our file
     * system.
     */
    private Map<String, Directory> mapOfDirectories;

    /**
     * Initialize the file system. If format = true, the disk has nothing on it,
     * and we need to initialize the disk to contain an empty directory, and a
     * bitmap of free sectors (with almost but not all of the sectors marked as
     * free).
     *
     * If format = false, we just have to open the files representing the bitmap
     * and the directory.
     *
     * @param diskDriver
     *            Access to the disk on which the filesystem resides.
     * @param format
     *            Should we initialize the disk?
     */
    protected FileSystemReal(DiskDriver diskDriver, boolean format) {
	Debug.print('f', "Initializing the file system.\n");
	this.diskDriver = diskDriver;
	numDiskSectors = diskDriver.getNumSectors();
	diskSectorSize = diskDriver.getSectorSize();
	FreeMapFileSize = (numDiskSectors / BitMap.BitsInByte);
	DirectoryFileSize = (DirectoryEntry.sizeOf() * NumDirEntries);

	// initialize the map

	if (format) {
	    BitMap freeMap = new BitMap(numDiskSectors);
	    // Directory directory = new Directory(NumDirEntries, this);
	    // passing root as the directory name
	    Directory rootDirectory = new Directory(NumDirEntries, this, "/");

	    // initialize the map
	    mapOfDirectories = new HashMap<>();
	    // add root directory to the map
	    this.mapOfDirectories.put("/", rootDirectory);

	    FileHeader mapHdr = new FileHeader(this);
	    FileHeader dirHdr = new FileHeader(this);

	    Debug.print('f', "Formatting the file system.\n");

	    // First, allocate space for FileHeaders for the directory and
	    // bitmap
	    // (make sure no one else grabs these!)
	    freeMap.mark(FreeMapSector);
	    freeMap.mark(DirectorySector);

	    // Second, allocate space for the data blocks containing the
	    // contents
	    // of the directory and bitmap files. There better be enough space!

	    Debug.ASSERT(mapHdr.allocate(freeMap, FreeMapFileSize));
	    Debug.ASSERT(dirHdr.allocate(freeMap, DirectoryFileSize));

	    // Flush the bitmap and directory FileHeaders back to disk
	    // We need to do this before we can "Open" the file, since open
	    // reads the file header off of disk (and currently the disk has
	    // garbage on it!).

	    Debug.print('f', "Writing headers back to disk.\n");
	    mapHdr.writeBack(FreeMapSector);
	    dirHdr.writeBack(DirectorySector);

	    // OK to open the bitmap and directory files now
	    // The file system operations assume these two files are left open
	    // while Nachos is running.

	    freeMapFile = new OpenFileReal(FreeMapSector, this);
	    directoryFile = new OpenFileReal(DirectorySector, this);

	    // Once we have the files "open", we can write the initial version
	    // of each file back to disk. The directory at this point is
	    // completely
	    // empty; but the bitmap has been changed to reflect the fact that
	    // sectors on the disk have been allocated for the file headers and
	    // to hold the file data for the directory and bitmap.

	    Debug.print('f', "Writing bitmap and directory back to disk.\n");
	    freeMap.writeBack(freeMapFile); // flush changes to disk
	    rootDirectory.writeBack(directoryFile);

	    if (Debug.isEnabled('f')) {
		freeMap.print();
		rootDirectory.print();
	    }

	} else {
	    // if we are not formatting the disk, just open the files
	    // representing
	    // the bitmap and directory; these are left open while Nachos is
	    // running
	    freeMapFile = new OpenFileReal(FreeMapSector, this);
	    directoryFile = new OpenFileReal(DirectorySector, this);
	}
    }

    /**
     * Read a sector of the filesystem, using the underlying disk driver.
     *
     * @param sectorNumber
     *            The disk sector to read.
     * @param data
     *            The buffer to hold the contents of the disk sector.
     * @param index
     *            Offset in the buffer at which to place the data.
     */
    void readSector(int sectorNumber, byte[] data, int index) {
	diskDriver.readSector(sectorNumber, data, index);
    }

    /**
     * Write a sector of the filesystem, using the underlying disk driver.
     *
     * @param sectorNumber
     *            The disk sector to be written.
     * @param data
     *            The new contents of the disk sector.
     * @param index
     *            Offset in the buffer from which to get the data.
     */
    void writeSector(int sectorNumber, byte[] data, int index) {
	diskDriver.writeSector(sectorNumber, data, index);
    }

    /**
     * Create a file in the Nachos file system (similar to UNIX create). Since
     * we can't increase the size of files dynamically, we have to supply the
     * initial size of the file.
     *
     * The steps to create a file are: Make sure the file doesn't already exist;
     * Allocate a sector for the file header; Allocate space on disk for the
     * data blocks for the file; Add the name to the directory; Store the new
     * file header on disk; Flush the changes to the bitmap and the directory
     * back to disk.
     *
     * Return true if everything goes ok, otherwise, return false.
     *
     * Create fails if: file is already in directory; no free space for file
     * header; no free entry for file in directory; no free space for data
     * blocks for the file.
     *
     * Note that this implementation assumes there is no concurrent access to
     * the file system!
     *
     * @param name
     *            The name of file to be created.
     * @param initialSize
     *            The size of file to be created.
     * @return true if the file was successfully created, otherwise false.
     */
    public boolean create(String name, long initialSize) {
	Directory directory;
	BitMap freeMap;
	FileHeader hdr;
	int sector;
	boolean success;

	Debug.printf('f', "Creating file %s, size %d\n", name,
		new Long(initialSize));

	directory = new Directory(NumDirEntries, this);
	directory.fetchFrom(directoryFile);

	if (directory.find(name) != -1)
	    success = false; // file is already in directory
	else {
	    freeMap = new BitMap(numDiskSectors);
	    freeMap.fetchFrom(freeMapFile);
	    sector = freeMap.find(); // find a sector to hold the file header
	    if (sector == -1)
		success = false; // no free block for file header
	    else if (!directory.add(name, sector))
		success = false; // no space in directory
	    else {
		hdr = new FileHeader(this);
		if (!hdr.allocate(freeMap, (int) initialSize))
		    success = false; // no space on disk for data
		else {
		    success = true;
		    // everything worked, flush all changes back to disk
		    hdr.writeBack(sector);
		    directory.writeBack(directoryFile);
		    freeMap.writeBack(freeMapFile);
		}
	    }
	}
	Debug.println('+', "File system creation status: " + success);
	return success;
    }

    /**
     * Creates a new directory with the specified name pathname as the directory
     * name.
     */
    public boolean createDirectory(String pathName) {

	if (pathName == "/")
	    return false;
	Directory childDirectory = new Directory(NumDirEntries, this, pathName);

	// TODO Check if required??
	allocateSpaceForDirectory(childDirectory);

	// check parent directory if it exists in map
	// if yes then extract from map and put it in the list
	int slashCount = getSlashCount(pathName);

	if (slashCount == 1) {
	    // create a sub directory in the root directory only
	    if (mapOfDirectories.get("/") != null) {
		((Directory) mapOfDirectories.get("/")).getDirectories()
			.add(childDirectory);

		// update the map
		mapOfDirectories.put(pathName, childDirectory);
		Debug.println('f', "Created new directory: " + pathName);
		return true;
	    } else {
		// root doesn't exist
		Debug.println('f', "Root Directory doesn't exist.");
		return false;
	    }
	} else {
	    // check if parent exists
	    String parentDirectoryName = pathName.substring(0,
		    pathName.lastIndexOf('/'));
	    Directory parentDirectory = mapOfDirectories
		    .get(parentDirectoryName);

	    if (parentDirectory != null) {
		// insert new directory(==child) in parent directory
		parentDirectory.getDirectories().add(childDirectory);
		Debug.println('f', "Created new directory: " + pathName);
		// update the map
		mapOfDirectories.put(pathName, childDirectory);

		return true;

	    } else {
		// Parent doesn't exist
		Debug.println('f', "Parent Directory: " + parentDirectoryName
			+ " doesn't exist.");
		return false;
	    }

	}
    }

    private void allocateSpaceForDirectory(Directory directory) {
	// TODO Copy the actions for a root directory from the constructor of
	// this class. We need to allocate space for this new directory as well.
	// May be ???

    }

    /**
     * Removes the directory.
     * 
     * @return
     */
    public boolean removeDirectory(String pathName) {
	Directory directory = mapOfDirectories.get(pathName);
	if (directory == null) {
	    Debug.println('f', "Directory :" + pathName
		    + " doesn't exist in the File System.");
	    return false;
	} else {
	    // Remove association of parent
	    // if it's root directory then there's no parent.

	    if (pathName != "/") {
		int slashCount = getSlashCount(pathName);
		Directory parentDirectory;
		if (slashCount == 1) {
		    parentDirectory = mapOfDirectories.get("/");

		} else {
		    // Get parent directory
		    String parentDirectoryName = pathName.substring(0,
			    pathName.lastIndexOf('/'));
		    parentDirectory = mapOfDirectories.get(parentDirectoryName);

		}
		for (int i = 0; i < parentDirectory.getDirectories()
			.size(); i++) {
		    if (parentDirectory.getDirectories().get(i)
			    .getDirectoryName().equalsIgnoreCase(pathName)) {
			parentDirectory.getDirectories().remove(i);
			break;
		    }
		}
	    }
	    removeDirectoryHelper(directory);
	    return true;
	}
    }

    /**
     * Free memory for all associated files in the directory. And recursively
     * call the method for all sub directorues.
     * 
     * @param directory
     */
    private void removeDirectoryHelper(Directory directory) {

	// First free all the table[i] elements in that directory and
	// its child directories that contain the file. We need to free
	// memory
	// from the files that occupies it. We need to recursively call the
	// free memory call to each subsequent child directory.
	freeMemory(directory);

	// call removeDirectory() for each of the child directories
	for (int i = 0; i < directory.getDirectories().size(); i++) {
	    removeDirectoryHelper(directory.getDirectories().get(i));
	}
	mapOfDirectories.put(directory.getDirectoryName(), null);
    }

    /**
     * Method to free all the associated memory blocks on disk for the table of
     * Directory Entry.
     * 
     * @param directory
     */
    private void freeMemory(Directory directory) {
	// remove all the associated files in the table[] array
	for (int i = 0; i < directory.getTable().length; i++) {
	    removeFileFromDirectory(directory.getTable()[i].getName(),
		    directory);
	}
    }

    private int getSlashCount(String pathName) {
	int count = 0;
	for (int i = 0; i < pathName.length(); i++) {
	    if (pathName.charAt(i) == '/')
		count++;
	}
	return count;
    }

    /**
     * Open a file for reading and writing. To open a file: Find the location of
     * the file's header, using the directory; Bring the header into memory.
     *
     * @param name
     *            The text name of the file to be opened.
     */
    public OpenFile open(String name) {
	Directory directory = new Directory(NumDirEntries, this);
	OpenFile openFile = null;
	int sector;

	Debug.printf('f', "Opening file %s\n", name);
	directory.fetchFrom(directoryFile);
	sector = directory.find(name);
	if (sector >= 0)
	    openFile = new OpenFileReal(sector, this);// name was found in
						      // directory
	return openFile; // return null if not found
    }

    /**
     * Delete a file from the file system. This requires: Remove it from the
     * directory; Delete the space for its header; Delete the space for its data
     * blocks; Write changes to directory, bitmap back to disk.
     *
     * Return true if the file was deleted, false if the file wasn't in the file
     * system.
     *
     * @param name
     *            The text name of the file to be removed.
     */
    public boolean remove(String name) {
	Directory directory;
	BitMap freeMap;
	FileHeader fileHdr;
	int sector;

	directory = new Directory(NumDirEntries, this);
	directory.fetchFrom(directoryFile);
	sector = directory.find(name);
	if (sector == -1) {
	    return false; // file not found
	}
	fileHdr = new FileHeader(this);
	fileHdr.fetchFrom(sector);

	freeMap = new BitMap(numDiskSectors);
	freeMap.fetchFrom(freeMapFile);

	fileHdr.deallocate(freeMap); // remove data blocks
	freeMap.clear(sector); // remove header block
	directory.remove(name);

	freeMap.writeBack(freeMapFile); // flush to disk
	directory.writeBack(directoryFile); // flush to disk
	return true;
    }

    /**
     * This method is copy of above method except the fact that it can work for
     * any directory. The above method only works for the root directory, i.e.
     * for the stock version of the nachos in which there's only one directory.
     * 
     * @param name
     * @return
     */
    public boolean removeFileFromDirectory(String name, Directory directory) {
	BitMap freeMap;
	FileHeader fileHdr;
	int sector;

	sector = directory.find(name);
	if (sector == -1) {
	    return false; // file not found
	}
	fileHdr = new FileHeader(this);
	fileHdr.fetchFrom(sector);

	freeMap = new BitMap(numDiskSectors);
	freeMap.fetchFrom(freeMapFile);

	fileHdr.deallocate(freeMap); // remove data blocks
	freeMap.clear(sector); // remove header block
	directory.remove(name);

	freeMap.writeBack(freeMapFile); // flush to disk
	directory.writeBack(directoryFile); // flush to disk
	return true;
    }

    /**
     * List all the files in the file system directory (for debugging).
     */
    // Extend the list method in the FileSystemReal class so that it
    // correctly lists all the files and directories on the system, and use this
    // method to demonstrate that your extensions are working correctly.

    public void list() {
	// Directory directory = new Directory(NumDirEntries, this);
	Directory directory = mapOfDirectories.get("/");

	// update the files from disk
	directory.fetchFrom(directoryFile);
	// Directory directory = mapOfDirectories.get("/");

	listHelper(directory);
    }

    private void listHelper(Directory directory) {
	Debug.println('f', "Listing all the file names in the directory:  "
		+ directory.getDirectoryName());
	directory.list();
	for (int i = 0; i < directory.getDirectories().size(); i++) {
	    listHelper(directory.getDirectories().get(i));
	}

    }

    /**
     * Print everything about the file system (for debugging): the contents of
     * the bitmap; the contents of the directory; for each file in the
     * directory: the contents of the file header; the data in the file.
     */
    public void print() {
	FileHeader bitHdr = new FileHeader(this);
	FileHeader dirHdr = new FileHeader(this);
	BitMap freeMap = new BitMap(numDiskSectors);
	Directory directory = new Directory(NumDirEntries, this);

	Debug.print('+', "Bit map file header:\n");
	bitHdr.fetchFrom(FreeMapSector);
	bitHdr.print();

	Debug.print('+', "Directory file header:\n");
	dirHdr.fetchFrom(DirectorySector);
	dirHdr.print();

	freeMap.fetchFrom(freeMapFile);
	freeMap.print();

	directory.fetchFrom(directoryFile);
	directory.print();

    }

    /**
     * TODO Implement a method in the FileSystemReal class that checks the file
     * system for consistency. In particular, it should detect the following
     * kinds of problems:
     */
    public void checkFileSystemForConsistency() {
	// part 1
	// iterate through all the sector numbers in the datasector array and
	// check the corresponding entry in the bitmap(=freeMap).

	// part 2
	// create a map<integer, boolean> that records the sector occupied by
	// files. Then iterate through the bitmap and check if corresponding
	// entry in the map is true.

	// part 3
	// similar to part 2. we create a map for sector entries and check if
	// the entry we are seeing in map should be already present.

    }

    public DiskDriver getDiskDriver() {
	return diskDriver;
    }

    public OpenFile getFreeMapFile() {
	return freeMapFile;
    }
}
