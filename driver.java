import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

public class driver {	
	public static void main (String[] args) {
		
		// check for arguments
		if(args.length == 0){
			System.out.println("Not enough arguments, try again");
			System.exit(0);
		}
		
		// cache global variables
		int cacheSize = 0;
		int blockSize = 0;
		int assoc = 0;
		int physMem = 0;
		int memUsed = 0;
		int timeSlice = 0;
		String policy = new String();
        ArrayList<File> fileList = new ArrayList<>();
		ArrayList<Tracefile> traceFileList = new ArrayList<>();
		
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
					File f = new File(args[++i]);
					fileList.add(f);
					traceFileList.add(new Tracefile(f));
					//traceFileList.add(new Tracefile(f.getName()));
					break;
				
				default:
					continue;
			}
		}
		
		//////////////////////////////////
		//		BEGIN MILESTONE #1		//
		//////////////////////////////////

		//print all to information to the screen and write to and output file
		System.out.println("Cache Simulator - CS 3853 - Group #05\n");
		printTraceFiles(fileList);
		printInputParams(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
		printCacheCalcs(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
		printPhysicalMemoryCalcs(physMem, memUsed, fileList.size());
		
		/*try (PrintStream out = new PrintStream(new FileOutputStream("Team_05_Sim_n_M#1.txt"))){
			System.setOut(out);
			System.out.println("Cache Simulator - CS 3853 - Group #05\n");
			printTraceFiles(fileList);
			printInputParams(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
			printCacheCalcs(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
			printPhysicalMemoryCalcs(physMem, memUsed, fileList.size());
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		//////////////////////////////////
		//		END MILESTONE #1		//
		//////////////////////////////////



		//////////////////////////////////
		//       BEGIN MILESTONE #2		//
		//////////////////////////////////
		
		//setup the cache
		//each block set to -1 to indicate valid is not set/no tag written
		int[][] arrCache = new int[CalculateNumOfSets(cacheSize,blockSize,assoc)][assoc];
			for (int row = 0; row < arrCache.length; row++){
				for (int col = 0; col < arrCache[row].length; col++){
					arrCache[row][col] = -1;
				}
			}
		
		//process each trace file, save info to variables and cache
		int totalAddressesRead = 0;			//+1 EIP address & +1src & +1dst (if src/dst read occurred) 
		int sumInstructionBytes = 0;		//sum of all the numbers in (_) after EIP
		int sumDstSrcBytes = 0;			//if src/dst read occurred, add 4 for either one where read occurred

		int doneCount = 0;
		for (int i = 0; doneCount < traceFileList.size(); i++){
			Tracefile currentFile = traceFileList.get(i);

			//loop back to beginning of trace files array
			if(i == traceFileList.size()) {
				i = 0;
			}

			if(currentFile.isDoneReading) {
				continue;
			}
		
			try
			{
				BufferedReader br = new BufferedReader(new FileReader(currentFile.filePath));
				String line = null;
				char[] charArray = new char[100];

				
				//reads every line in the file
				//TODO: need to adjust for TimeSlice
				while (!currentFile.isDoneReading)
				{
					line = br.readLine();
					if (line == null){
						currentFile.isDoneReading = true;
						doneCount++;
						break;
					}
					//convert line to a char array
					charArray = line.toCharArray();
					if(charArray.length != 0)
					{
						switch(charArray[0])
						{
							//process the instruction address, hex address is [10-17]
							case 'E':
								String eipNum = new StringBuilder().append(charArray[5]).append(charArray[6]).toString();
								sumInstructionBytes += Integer.parseInt(eipNum);
								StringBuilder sbEipAddress = new StringBuilder();
								for (int j = 10; j <= 17; j++)
								{
									sbEipAddress.append(charArray[j]);
								}
								String eipAddress = sbEipAddress.toString();
								//TODO: send the eip address for cache hit/miss check
								totalAddressesRead++;
								break;
							
							//process both dst[6-13][15-22] and src[33-40][42-49] addresses
							case 'd':
								//dstM:
								String dstAddress = null;
								StringBuilder sbDst = new StringBuilder();
								for (int j = 6; j <= 13; j++)
								{
									sbDst.append(charArray[j]);
								}
								dstAddress = sbDst.toString();
								if (!dstAddress.equals("00000000"))
								{
									//TODO: send dst address for cache hit/miss check
									sumDstSrcBytes += 4;
									totalAddressesRead++;
								}

								//srcM:
								String srcAddress = null;
								StringBuilder sbSrc = new StringBuilder();
								for (int j = 33; j <= 40; j++)
								{
									sbSrc.append(charArray[j]);
								}
								srcAddress = sbSrc.toString();
								if (!srcAddress.equals("00000000"))
								{
									//TODO: send src address for cache hit/miss check
									sumDstSrcBytes += 4;
									totalAddressesRead++;
								}

							default:
								break;
						}
					}
				}
				br.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		//print cache results
		System.out.println("\n*****CACHE SIMULATION RESULTS*****\n");
		System.out.println("Total Cache Accesses: \t" + "------" + "\t(" + totalAddressesRead + " addresses)");
		System.out.print("Instruction Bytes: \t" + sumInstructionBytes);
		System.out.println("\tSrcDst Bytes: " + sumDstSrcBytes);
	}
	
	
	
	// prints the input parameters
	public static void printInputParams(int cacheSize, int blockSize, int assoc, int physMem, int memUsed, int timeSlice, String policy){
		DecimalFormat df = new DecimalFormat("0.0");
		
		System.out.println("***** Cache Input Parameters *****\n");
		System.out.println("Cache size:                     " + cacheSize + " KB");
		System.out.println("Block size:                     " + blockSize + " bytes");
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
	public static void printPhysicalMemoryCalcs(long physMem, int memUsed, int numTraceFiles) {
        int pageSize = 4096; 
        long numOfPhysPages = (physMem * 1024 * 1024) / pageSize;
        long numOfPagesForSystem = (long) (numOfPhysPages * (memUsed / 100.0));
		long sizeOfPageTableEntry = 0;
		try{
			sizeOfPageTableEntry = CalculateLogBase2(numOfPhysPages) + 1; //bits needed for number of physical pages + 1 valid bit
		} catch(Exception e)
			{
				System.out.println("Error in Size of Page Table Calculation");
				System.exit(-1);
			}
        int numEntries = 512 * 1024; //all trace file addresses < 0x7FFFFFFF, so 512KB hard limit
        long totalRAMForPageTables = (numEntries * sizeOfPageTableEntry * numTraceFiles) / 8;

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
	
	public static long CalculateLogBase2(long arg){
		long base = 2;
		return (long) (Math.log(arg) / Math.log(base));
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
	
	//Tracefile Class
	public static class Tracefile{
		public File filePath = null;
		public String name = null;
		public int currReadPos;
		public boolean isDoneReading;
		
		public Tracefile(File filePath){
			this.filePath = filePath;
			currReadPos = 0;
			isDoneReading = false;
		}

		/*public Tracefile(String name){
			name = this.name;
			filePath = new File(name);
			currReadPos = 0;
			isDoneReading = false;
		}*/
		
		public String toString() {
			return 	"Filepath " + filePath.getName()
				+	"Name " + name
				+	"currReadPos " + currReadPos
				+ 	"Done Status " + Boolean.toString(isDoneReading);
		}
	}
}	