import java.io.File;
import java.util.ArrayList;
import java.text.DecimalFormat;

public class driver {	
	public static void main (String[] args) {
		
		// check for arguments
		if(args.length == 0){
			System.out.println("Not enough arguments, try again");
			System.exit(0);
		}
		
		// variables
		int cacheSize = 0;
		int blockSize = 0;
		int assoc = 0;
		int physMem = 0;
		int memUsed = 0;
		int timeSlice = 0;
		String policy = new String();
        ArrayList<File> fileList = new ArrayList<>();
		
        // stores arguments into respective variables
		for (int i = 0; i < args.length; i++) {
			switch(args[i]){
				case "-s":
					cacheSize = Integer.parseInt(args[++i]);
					break;
				
				case "-b":
					blockSize = Integer.parseInt(args[++i]);
					break;
				
				case "-a":
					assoc = Integer.parseInt(args[++i]);
					break;
					
				case "-r":
					policy = (args[++i].equalsIgnoreCase("RR")) ? "Round Robin" : "Random";
					break;
				
				case "-p":
					physMem = Integer.parseInt(args[++i]);
					break;
				
				case "-u":
					memUsed = Integer.parseInt(args[++i]);
					break;
				
				case "-n":
					timeSlice = Integer.parseInt(args[++i]);
					break;
					
				case "-f":
					fileList.add(new File(args[++i]));
					break;
				
				default:
					continue;
			}
		}
		
		System.out.println("\nCache Simulator - CS 3853 - Group #05\n");
		printTraceFiles(fileList);
		printInputParams(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
		printCacheCalcs(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
		printPhysicalMemoryCalcs(physMem, memUsed, fileList.size());
	}
	
	// prints the input parameters
	public static void printInputParams(int cacheSize, int blockSize, int assoc, int physMem, int memUsed, int timeSlice, String policy){
		DecimalFormat df = new DecimalFormat("0.0");
		
		System.out.println("***** Cache Input Parameters *****\n");
		System.out.println("Cache size:                     " + cacheSize);
		System.out.println("Block size:                     " + blockSize);
		System.out.println("Associativity:                  " + assoc);
		System.out.println("Replacement Policy:             " + policy);
		System.out.println("Physical Memory:                " + physMem);
		System.out.println("Percent Memory Used by System:  " + df.format(memUsed) + "%");
		System.out.println("Instructions / Time Slice:      " + timeSlice);
	}
	
	// prints trace files
	public static void printTraceFiles(ArrayList<File> fileList) {
		System.out.println("Trace File(s):");
		for (File f : fileList){		
			System.out.println("\t" + f.getName());
		}
  		System.out.println();
	}
	
	// prints the cache calculations
	public static void printCacheCalcs(int cacheSize, int blockSize, int assoc, int physMem, int memUsed, int timeSlice, String policy){
		
		int addressSpace = 32;
		int blockOffset = CalculateLogBase2(blockSize);
		DecimalFormat df = new DecimalFormat("0.00");
		cacheSize *= 1024; //converting to KB
		
        System.out.println("\n***** Cache Calculated Values *****");
		System.out.println("");
        int numOfBlocks = (cacheSize / blockSize);
        System.out.println("Total # Blocks:\t\t\t" + numOfBlocks);
		
		int numOfIndices = CalculateNumOfIndices(cacheSize, blockSize, assoc);
		int tagBits = CalculateTagBits(addressSpace,blockOffset,CalculateLogBase2(numOfIndices));
        System.out.println("Tag Size:\t\t\t" + tagBits + " bits");
        System.out.println("Index Size:\t\t\t" + CalculateLogBase2(numOfIndices) + " bits");
		
		int numOfSets = CalculateNumOfSets(cacheSize,blockSize,assoc);
        System.out.println("Total # Rows:\t\t\t" + numOfSets);

		int cacheTax = CalculateCacheTax(assoc,tagBits,numOfIndices);
		System.out.println("Overhead Size:\t\t\t" + cacheTax + " bytes");

		double impSizeKB = (numOfBlocks*blockSize + CalculateCacheTax(assoc,tagBits,numOfIndices)) / Math.pow(2,10);
		int impSize = (numOfBlocks*blockSize + CalculateCacheTax(assoc,tagBits,numOfIndices));
		System.out.println("Implementation Memory Size:\t" + df.format(impSizeKB) + " KB  (" + impSize + " bytes)");
		
		double cost = (((numOfBlocks*blockSize + CalculateCacheTax(assoc,tagBits,numOfIndices)) / Math.pow(2,10))*0.15);
		System.out.println("Cost:\t\t\t\t$" + df.format(cost) + " @ $0.15 per KB");
		
	}
	
	// prints physical memory calculations
	public static void printPhysicalMemoryCalcs(int physMem, int memUsed, int numTraceFiles) {
        int pageSize = 4096; 
        int numOfPhysPages = (physMem * 1024 * 1024) / pageSize;
        int numOfPagesForSystem = (int) (numOfPhysPages * (memUsed / 100.0));
        int sizeOfPageTableEntry = 19;
        int numEntries = 512 * 1024;
        int totalRAMForPageTables = (numEntries * sizeOfPageTableEntry * numTraceFiles) / 8;

        System.out.println("\n***** Physical Memory Calculated Values *****");
        System.out.println("");
        System.out.println("Number of Physical Pages:\t" + numOfPhysPages);
        System.out.println("Number of Pages for System:\t" + numOfPagesForSystem);
        System.out.println("Size of Page Table Entry:\t" + sizeOfPageTableEntry + " bits");
        System.out.println("Total RAM for Page Table(s):\t" + totalRAMForPageTables + " bytes");
    }
	
	
	// these methods needed for cache calculations
	// may consider putting these is a private class to make things cleaner
	public static int CalculateLogBase2(int arg){
        int base = 2;
        return (int) (Math.log(arg) / Math.log(base));
    }

    public static int CalculateNumOfIndices(int cacheSize, int blockSize, int assoc){
        return cacheSize / (blockSize * assoc);
    }

    public static int CalculateTagBits(int addressSpace, int byteOffset, int indexBits){
        return addressSpace - byteOffset - indexBits;
    }

    public static int CalculateCacheTax(int assoc, int tagBits, int numOfIndices){
        return assoc * (1 + tagBits) * numOfIndices / 8; //last 8 is for converting to Bytes
    }

    public static int CalculateNumOfSets(int cacheSize, int blockSize, int assoc){
        return cacheSize / (blockSize * assoc);
    }
}	