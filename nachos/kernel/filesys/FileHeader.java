// FileHeader.jave
//	Routines for managing the disk file header (in UNIX, this
//	would be called the i-node).
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.filesys;

import nachos.Debug;
import nachos.kernel.Nachos;

/**
 * This class defines the Nachos "file header" (in UNIX terms, the "i-node"),
 * describing where on disk to find all of the data in the file. The file header
 * is organized as a simple table of pointers to data blocks.
 *
 * The file header data structure can be stored in memory or on disk. When it is
 * on disk, it is stored in a single sector -- this means that we assume the
 * size of this data structure to be the same as one disk sector. Without
 * indirect addressing, this limits the maximum file length to just under 4K
 * bytes.
 *
 * The file header is used to locate where on disk the file's data is stored. We
 * implement this as a fixed size table of pointers -- each entry in the table
 * points to the disk sector containing that portion of the file data (in other
 * words, there are no indirect or doubly indirect blocks). The table size is
 * chosen so that the file header will be just big enough to fit in one disk
 * sector,
 *
 * Unlike in a real system, we do not keep track of file permissions, ownership,
 * last modification date, etc., in the file header.
 *
 * A file header can be initialized in two ways: for a new file, by modifying
 * the in-memory data structure to point to the newly allocated data blocks; for
 * a file already on disk, by reading the file header from disk.
 *
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
class FileHeader {

    /** Number of pointers to data blocks stored in a file header. */
    private final int NumDirect;

    /** Maximum file size that can be represented in the baseline system. */
    private final int MaxFileSize = 138752; // (28 + 32 + 32*32)*128 = 138752
					    // bytes.

    /** Number of bytes in the file. */
    private int numBytes;

    /** Number of data sectors in the file. */
    private int numSectors;

    /** Disk sector numbers for each data block in the file. */
    private int dataSectors[];

    /** The underlying filesystem in which the file header resides. */
    private final FileSystemReal filesystem;

    /** Disk sector size for the underlying filesystem. */
    private final int diskSectorSize;

    // Long Files addition
    /**
     * Single indirect block that holds 32 sectors. First two sectors will be
     * copied from last two entries the array of the array of 30 sectors.
     */
    private int[] singleIndirectBlock;

    /** Size of the indirect block. */
    private final int IndirectBlockSize = 32;

    private int[][] doubleIndirectBlock;

    private int columnIndex = 0;

    private int rowIndex = 0;

    /**
     * Allocate a new "in-core" file header.
     * 
     * @param filesystem
     *            The underlying filesystem in which the file header resides.
     */
    FileHeader(FileSystemReal filesystem) {
	this.filesystem = filesystem;
	diskSectorSize = filesystem.diskSectorSize;
	NumDirect = ((diskSectorSize - 2 * 4) / 4); // (128-2*4)/4 =30
	// MaxFileSize = (NumDirect * diskSectorSize); // 30*128 =3840

	dataSectors = new int[NumDirect];
	// Safest to fill the table with garbage sector numbers,
	// so that we error out quickly if we forget to initialize it properly.
	for (int i = 0; i < NumDirect; i++)
	    dataSectors[i] = -1;

	// Initialize the single and double blocks
	singleIndirectBlock = new int[IndirectBlockSize];
	for (int i = 0; i < singleIndirectBlock.length; i++) {
	    singleIndirectBlock[i] = -1;
	}
	doubleIndirectBlock = new int[IndirectBlockSize][IndirectBlockSize];
	for (int i = 0; i < doubleIndirectBlock.length; i++) {
	    for (int j = 0; j < doubleIndirectBlock[0].length; j++) {
		doubleIndirectBlock[i][j] = -1;
	    }

	}
    }

    // the following methods deal with conversion between the on-disk and
    // the in-memory representation of a DirectoryEnry.
    // Note: these methods must be modified if any instance variables
    // are added!!

    /**
     * Initialize the fields of this FileHeader object using data read from the
     * disk.
     *
     * @param buffer
     *            A buffer holding the data read from the disk.
     * @param pos
     *            Position in the buffer at which to start.
     */
    private void internalize(byte[] buffer, int pos) {
	numBytes = FileSystem.bytesToInt(buffer, pos);
	numSectors = FileSystem.bytesToInt(buffer, pos + 4);
	for (int i = 0; i < NumDirect; i++)
	    dataSectors[i] = FileSystem.bytesToInt(buffer, pos + 8 + i * 4);
    }

    /**
     * Export the fields of this FileHeader object to a buffer in a format
     * suitable for writing to the disk.
     *
     * @param buffer
     *            A buffer into which to place the exported data.
     * @param pos
     *            Position in the buffer at which to start.
     */
    private void externalize(byte[] buffer, int pos) {
	FileSystem.intToBytes(numBytes, buffer, pos);
	FileSystem.intToBytes(numSectors, buffer, pos + 4);
	for (int i = 0; i < NumDirect; i++)
	    FileSystem.intToBytes(dataSectors[i], buffer, pos + 8 + i * 4);
    }

    /**
     * Initialize a fresh file header for a newly created file. Allocate data
     * blocks for the file out of the map of free disk blocks. Return FALSE if
     * there are not enough free blocks to accomodate the new file.
     *
     * @param freeMap
     *            is the bit map of free disk sectors.
     * @param fileSize
     *            is size of the new file.
     */
    boolean allocate(BitMap freeMap, int fileSize) {
	if (fileSize > MaxFileSize) {
	    return false; // file too large
	}
	// when data can be fit in the array of 30 sectors
	else if (fileSize <= 3840) {

	    numBytes = fileSize;
	    numSectors = fileSize / diskSectorSize;
	    if (fileSize % diskSectorSize != 0)
		numSectors++;

	    if (freeMap.numClear() < numSectors || NumDirect < numSectors)
		return false; // not enough space

	    for (int i = 0; i < numSectors; i++)
		dataSectors[i] = freeMap.find();
	    return true;
	}
	// when we need the single indirect block
	else if (fileSize > 3840 && fileSize <= 7680) {

	    numBytes = fileSize;
	    numSectors = fileSize / diskSectorSize;
	    if (fileSize % diskSectorSize != 0)
		numSectors++;

	    if (freeMap.numClear() < numSectors)
		return false; // not enough space

	    for (int i = 0; i < dataSectors.length; i++) {
		dataSectors[i] = freeMap.find();
	    }
	    int sectorsForIndirectBlock = numSectors - dataSectors.length;
	    singleIndirectBlock[0] = dataSectors[28];
	    singleIndirectBlock[1] = dataSectors[29];

	    for (int i = 2; i < sectorsForIndirectBlock; i++) {
		singleIndirectBlock[i] = freeMap.find();
	    }
	    return true;
	}
	// when we need both indirect blocks
	else {
	    numBytes = fileSize;
	    numSectors = fileSize / diskSectorSize;
	    if (fileSize % diskSectorSize != 0)
		numSectors++;

	    if (freeMap.numClear() < numSectors)
		return false; // not enough space

	    for (int i = 0; i < dataSectors.length; i++) {
		dataSectors[i] = freeMap.find();
	    }
	    singleIndirectBlock[0] = dataSectors[28];
	    singleIndirectBlock[1] = dataSectors[29];

	    for (int i = 2; i < singleIndirectBlock.length; i++) {
		singleIndirectBlock[i] = freeMap.find();
	    }

	    int sectorsForDoubleIndirectBlock = numSectors - dataSectors.length
		    - singleIndirectBlock.length + 2;
	    int count = sectorsForDoubleIndirectBlock;
	    outerloop: for (int i = 0; i < doubleIndirectBlock.length; i++) {
		for (int j = 0; j < doubleIndirectBlock[0].length; j++) {
		    if (count == 0) {
			this.rowIndex = i;
			this.columnIndex = j;
			break outerloop;
		    }
		    doubleIndirectBlock[i][j] = freeMap.find();
		    count--;
		}
	    }
	    return true;
	}
    }

    /**
     * De-allocate all the space allocated for data blocks for this file.
     *
     * @param freeMap
     *            is the bit map of free disk sectors.
     */
    void deallocate(BitMap freeMap) {
	for (int i = 0; i < numSectors; i++) {
	    Debug.ASSERT(freeMap.test(dataSectors[i])); // ought to be marked!
	    freeMap.clear(dataSectors[i]);
	}
    }

    /**
     * Fetch contents of file header from disk.
     *
     * @param sector
     *            is the disk sector containing the file header.
     */
    void fetchFrom(int sector) {
	byte buffer[] = new byte[diskSectorSize];
	filesystem.readSector(sector, buffer, 0);
	internalize(buffer, 0);
    }

    /**
     * Write the modified contents of the file header back to disk.
     *
     * @param sector
     *            is the disk sector to contain the file header.
     */
    void writeBack(int sector) {
	byte buffer[] = new byte[diskSectorSize];
	externalize(buffer, 0);
	filesystem.writeSector(sector, buffer, 0);
    }

    /**
     * Calculate which disk sector is storing a particular byte within the file.
     * This is essentially a translation from a virtual address (the offset in
     * the file) to a physical address (the sector where the data at the offset
     * is stored).
     *
     * @param offset
     *            The location within the file of the byte in question.
     * @return the disk sector number storing the specified byte.
     */
    int byteToSector(int offset) {
	return (dataSectors[offset / diskSectorSize]);
    }

    /**
     * Retrieve the number of bytes in the file.
     *
     * @return the number of bytes in the file.
     */
    int fileLength() {
	return numBytes;
    }

    /**
     * Print the contents of the file header, and the contents of all the data
     * blocks pointed to by the file header.
     */
    void print() {
	int i, j, k;
	byte data[] = new byte[diskSectorSize];

	System.out.print("FileHeader contents.  File size: " + numBytes
		+ ".,  File blocks: ");
	for (i = 0; i < numSectors; i++)
	    System.out.print(dataSectors[i] + " ");

	System.out.println("\nFile contents:");
	for (i = k = 0; i < numSectors; i++) {
	    filesystem.readSector(dataSectors[i], data, 0);
	    for (j = 0; (j < diskSectorSize) && (k < numBytes); j++, k++) {
		if ('\040' <= data[j] && data[j] <= '\176') // isprint(data[j])
		    System.out.print((char) data[j]);
		else
		    System.out
			    .print("\\" + Integer.toHexString(data[j] & 0xff));
	    }
	    System.out.println();
	}
    }

    public int getMaxFileSize() {
	return MaxFileSize;
    }

    /**
     * This allocates the additional sectors in the memory.
     * 
     * @param additionalSectorsRequired
     */
    public int allocateAdditionalSectors(int additionalSectorsRequired) {
	int numDiskSectors = ((FileSystemReal) Nachos.fileSystem)
		.getDiskDriver().getNumSectors();
	OpenFile freeMapFile = ((FileSystemReal) Nachos.fileSystem)
		.getFreeMapFile();
	int sectorsAllocated;

	BitMap freeMap;

	freeMap = new BitMap(numDiskSectors);
	freeMap.fetchFrom(freeMapFile);

	if (freeMap.numClear() == 0) {
	    // no free space available
	    Debug.println('f',
		    "No free space available for allocation.; freeMap.numClear() == 0;");
	    return 0;
	} else if (freeMap.numClear() < additionalSectorsRequired) {
	    // Less free space available for allocation.
	    sectorsAllocated = freeMap.numClear();
	} else {
	    // freeMap.numClear() > additionalSectorsRequired
	    sectorsAllocated = additionalSectorsRequired;
	}

	int numSectorsNew = numSectors + sectorsAllocated;
	// based on sectors allocated we update the data

	// use only dataTable array in this case
	if (numSectorsNew <= 28) {
	    for (int i = numSectors; i < numSectors + sectorsAllocated; i++) {
		dataSectors[i] = freeMap.find();
	    }

	} // use single array
	else if (numSectorsNew <= 60) {

	    int sectorsForIndirectBlock = numSectors - dataSectors.length;
	    singleIndirectBlock[0] = dataSectors[28];
	    singleIndirectBlock[1] = dataSectors[29];

	    for (int i = 2; i < sectorsForIndirectBlock; i++) {
		singleIndirectBlock[i] = freeMap.find();
	    }

	} // also use double array
	else {
	    singleIndirectBlock[0] = dataSectors[28];
	    singleIndirectBlock[1] = dataSectors[29];

	    int sectorsForDoubleIndirectBlock = numSectorsNew
		    - dataSectors.length - singleIndirectBlock.length + 2;
	    int noOfRows = (sectorsForDoubleIndirectBlock
		    - sectorsForDoubleIndirectBlock % 32) / 32;

	    int count = sectorsForDoubleIndirectBlock;
	    for (int j = this.columnIndex; j < 32; j++) {
		// fill the entire column for these many rows.
		if (count == 0)
		    break;
		doubleIndirectBlock[this.rowIndex][j] = freeMap.find();
		count--;
	    }

	    outerloop: for (int i = this.rowIndex + 1; i < noOfRows; i++) {
		for (int j = 0; j < 32; j++) {
		    // fill the entire column for these many rows.
		    if (count == 0)
			break outerloop;
		    doubleIndirectBlock[i][j] = freeMap.find();
		    count--;
		}
	    }
	}

	// update numsectors
	this.numSectors += sectorsAllocated;

	// update filesize
	this.numBytes = numSectors * diskSectorSize;

	Debug.println('f', "Additional sectors allocated: " + sectorsAllocated);
	return sectorsAllocated;
    }

    private int getStartingColIndex(int startingRowIndex) {
	for (int i = 0; i < doubleIndirectBlock[0].length; i++) {
	    if (doubleIndirectBlock[startingRowIndex][i] == -1)
		return i;
	}
	return doubleIndirectBlock[0].length;
    }

    private int getStartingRowIndex() {
	for (int i = 0; i < doubleIndirectBlock.length; i++) {
	    if (doubleIndirectBlock[i][0] == -1)
		return i == 0 ? 0 : i - 1;
	}
	return doubleIndirectBlock.length;
    }

    /**
     * Increases the size of the file to the maximum size supported by the
     * system.
     */
    public int extendFileLengthToMaximumSize() {
	int numDiskSectors = ((FileSystemReal) Nachos.fileSystem)
		.getDiskDriver().getNumSectors();
	OpenFile freeMapFile = ((FileSystemReal) Nachos.fileSystem)
		.getFreeMapFile();

	BitMap freeMap;
	freeMap = new BitMap(numDiskSectors);
	freeMap.fetchFrom(freeMapFile);

	int sectorsToBeAllocated = MaxFileSize / diskSectorSize - numSectors;
	int sectorsAvailable = freeMap.numClear();

	if (sectorsAvailable < sectorsToBeAllocated) {
	    // not enough space
	    Debug.println('f', "Not Enough space available for all sectors: "
		    + sectorsToBeAllocated);
	    Debug.println('f',
		    "Only space available for sectors: " + sectorsAvailable);
	    for (int i = numSectors; i < numSectors + sectorsAvailable; i++) {
		dataSectors[i] = freeMap.find();
	    }
	    // update numsectors
	    numSectors = numSectors + sectorsAvailable;

	} else {
	    // enough space available
	    Debug.println('f', " Enough space available for all sectors: "
		    + sectorsToBeAllocated);
	    for (int i = numSectors; i < numSectors
		    + sectorsToBeAllocated; i++) {
		dataSectors[i] = freeMap.find();
	    }
	    // update numsectors
	    numSectors = numSectors + sectorsToBeAllocated;
	}

	// update file length
	numBytes = numSectors * diskSectorSize;
	return numSectors;
    }

    public int getNumSectors() {
	return numSectors;
    }

    public void setNumSectors(int numSectors) {
	this.numSectors = numSectors;
    }

    public int[] getDataSectors() {
	return dataSectors;
    }

    public void setDataSectors(int[] dataSectors) {
	this.dataSectors = dataSectors;
    }
}
