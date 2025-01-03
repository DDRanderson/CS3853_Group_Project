import java.io.BufferedReader;
import java.io.File;
import java.util.*;
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
		ArrayList<int[]> pageTableList = new ArrayList<>();


		
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
					pageTableList.add(new int[524288]);	
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

		////
		// 
		//	GLOBAL VARIABLES needed for Milestone#2
		//
		////
		int addressSpace = 32;
		int blockOffset = CalculateLogBase2(blockSize);		//the number of bits for the block offset
		cacheSize *= 1024; //this needs to be fixed later for our print to file output!!
		int numOfIndices = CalculateNumOfIndices(cacheSize, blockSize, assoc);
		int numOfSets = CalculateNumOfSets(cacheSize,blockSize,assoc);
		int tagBits = CalculateTagBits(addressSpace,blockOffset,CalculateLogBase2(numOfIndices));	//number of tag bits
		int indexBits = CalculateLogBase2(numOfIndices);		//number of index bits
		int sumCacheHits = 0;
		int sumCompulsoryMisses = 0;
		int sumConflictMisses = 0;
		int timeSliceLinesToRead = timeSlice * 3;


		////
		//
		// GLOBAL VARIABLES needed for Milestone #3 
		//
		////
		int pageSize = 4096; 
        long numOfPhysPages = (physMem * 1024 * 1024) / pageSize;
        long numOfPagesForSystem = (long) (numOfPhysPages * (memUsed / 100.0));
		int pagesAvailableToUser = (int)numOfPhysPages - (int)numOfPagesForSystem;
		int totalMappedVirtualPages = 0;
		int totalPageTableHits = 0;
		int totalPagesFromFree = 0;
		int totalPageFaults = 0;

		initializePTList(pageTableList);

		//Linked List of all available pages that aren't being used by system
		LinkedList<Integer> freeUserPagesList = new LinkedList<>();
		for (int p = 0; p < pagesAvailableToUser; p++) {
            freeUserPagesList.add(p);
		}

		//Linked List of all used pages from list of User Pages
		LinkedList<Integer> usedUserPagesList = new LinkedList<>();
		

		//setup the cache
		//each block set to -1 to indicate valid is not set/no tag written
		int[][] arrCache = new int[CalculateNumOfSets(cacheSize,blockSize,assoc)][assoc];
			for (int row = 0; row < arrCache.length; row++){
				for (int col = 0; col < arrCache[row].length; col++){
					arrCache[row][col] = -1;
				}
			}

		//keeps track of rows in the cache
		//this is used exclusively for the Round Robin replacement algorithm
		int[] replacementIndex = new int[numOfSets];
		
		//process each trace file, save info to variables and cache
		int totalAddressesRead = 0;			//+1 EIP address & +1src & +1dst (if src/dst read occurred) 
		int sumInstructionBytes = 0;		//sum of all the numbers in (_) after EIP
		int sumDstSrcBytes = 0;				//if src/dst read occurred, add 4 for either one where read occurred
		
		int totalCacheAccesses = 0;			//total # of cache accesses
		int totalCacheMisses = 0;			//total # of cache misses
		int totalCycles = 0;				// total # cycles
		int numReads = (int) Math.ceil((double) blockSize / 4); //number of memory reads to populate cache block
		int totalInstructions = 0;
		
		/* NEW FOR LOOP CODE TO READ FILES MORE EFFICIENTLY*/
		List<BufferedReader> readers = new ArrayList<>();
		int doneCount = 0;
		try {
			//creates list of buffered readers to keep each trace file open
			for (File filePath : fileList) {
                BufferedReader reader = new BufferedReader(new FileReader(filePath));
                readers.add(reader);
			}
		
			String line = null;
			char[] charArray = new char[100];
			//reads from all trace files until all trace files are done reading
			//index i is being used by both traceFileList, readers list, and pageTableList
			for (int i = 0; doneCount < traceFileList.size(); i++){
				//loop back to beginning of trace files array
				if(i == traceFileList.size()) {
					i = 0;
				}

				Tracefile currentTraceFile = traceFileList.get(i);

				if(currentTraceFile.isDoneReading) {
					continue;
				}

				int numOfLinesRead = 0;
				//reads lines in the file
				while (!currentTraceFile.isDoneReading)
				{
					line = readers.get(i).readLine(); 	 
					numOfLinesRead++;

					//Break out if we hit the final line to read
					if (numOfLinesRead == timeSliceLinesToRead && traceFileList.size() != 1){
						break;
					}

					if (line == null){
						currentTraceFile.isDoneReading = true;
						//once file is done reading, we must "free" all assigned User Page
						int[] temp = pageTableList.get(i);
						for (int t : temp ){
							if (t != -1){
								freeUserPagesList.add(t);
							}
						}
						doneCount++;
						break;
					}

					//convert line to a char array
					charArray = line.toCharArray();
					if(charArray.length != 0)
					{
						switch(charArray[0])
						{
							//process the INSTRUCTION ADDRESS, hex address is [10-17]
							case 'E':

								String eipNum = new StringBuilder().append(charArray[5]).append(charArray[6]).toString();
									if (Integer.parseInt(eipNum) == 0) continue;	//check for eip not reading any bytes
								totalAddressesRead++;
								sumInstructionBytes += Integer.parseInt(eipNum);
								StringBuilder sbEipAddress = new StringBuilder();
								for (int j = 10; j <= 17; j++)
								{
									sbEipAddress.append(charArray[j]);
								}
								String eipAddress = sbEipAddress.toString();	//a hex string

								///  MILESTONE 3 STUFF HERE
								/// break address into two parts
								/// 	PAGE NUMBER = top 20 bits
								/// 	PAGE OFFSET = bottom 12 bits
								int eipPageNumber = getPageNumBits(eipAddress);
								int eipPageOffset = getPageOffsetBits(eipAddress);

								if (eipPageNumber >= 1048575){
									continue;
								}
								/// check the current trace files Page Table: pageTabeList[pageNumber] == ?
								int[] eipPageTable = pageTableList.get(i);
								if (eipPageTable[eipPageNumber] != -1){		//page table hit
									totalPageTableHits++;
								} else if (eipPageTable[eipPageNumber] == -1){		//page table miss
									if(freeUserPagesList.isEmpty()){
										totalPageFaults++;
										// pick from another random file first, will choose self if only trace file
										int checker = 0;
										for (int j = i; j <= traceFileList.size(); j++){
											if (j == traceFileList.size()){
												j = 0;
											}

											Tracefile tempTrace = traceFileList.get(j);
											if (checker == traceFileList.size()){
												eipPageTable[eipPageNumber] = FindFirstFreePage(pageTableList.get(i));
												break;
											}
											if (j == i){
												checker++;
												continue;
											}
											if (tempTrace.isDoneReading){
												checker++;
												continue;
											} 
											
											//pull from trace file j
											eipPageTable[eipPageNumber] = FindFirstFreePage(pageTableList.get(j));
											break;
										}
										
									} else{
										eipPageTable[eipPageNumber] = freeUserPagesList.getFirst();
										freeUserPagesList.removeFirst();
										totalPagesFromFree++;
									}
									
								}

								/// create physical address from User Page and Page Offset
								/// ex] 0x12345678
								/// 	0x12345 is the USER PAGE pulled from freeUserPagesList
								/// 	0x678 is the PAGE OFFSET(bottom 12 bits)
								long longEIPAddress = (eipPageTable[eipPageNumber] * 4096) + eipPageOffset;
								eipAddress = String.format("%08X", longEIPAddress);
							
								/// run this new Physical Address through Milestone 2 caching algorithm
								/// MILESTONE 3 STUFF ENDS HERE!!

								//perform eip cache check and update cache if necessary
								int eipTag = parseTagBitsToInt(toBinaryString(eipAddress), tagBits);
								int eipIndex = parseIndexBitsToInt(toBinaryString(eipAddress), tagBits, indexBits);
								int eipBlock =  parseBlockBitsToInt(toBinaryString(eipAddress), tagBits, indexBits, blockOffset);
								int eipAddBlock = addBlockRows(eipBlock, Integer.parseInt(eipNum), blockSize);
								for (int row = eipIndex; row < (eipIndex + eipAddBlock + 1); row++){
									if (row >= arrCache.length) break; //OOB check
									int conflictCheckCount = 0;
									for (int col = 0; col < assoc; col++){
										if (col >= arrCache[row].length) break; //OOB check
									//arrCache[row][col] = eipTag;
									//every col is checked before deciding if hit/miss

										//checks for hit/matching tag
										if (arrCache[row][col] == eipTag){
											sumCacheHits++;
											totalCycles += 1; // cache hit; add 1 to total cycles
											break;
										}
										//checks for compulsory miss 
										else if (arrCache[row][col] < 0){
											arrCache[row][col] = eipTag;
											sumCompulsoryMisses++;
											totalCycles += (4 * numReads); // cache miss; 4 * num reads to populate block
											break;
										}
										//checks for conflict miss
										else if (arrCache[row][col] != eipTag){
											conflictCheckCount++;
											if (conflictCheckCount == assoc){
												sumConflictMisses++;
												totalCycles += (4 * numReads); // cache miss; 4 * num reads to populate block
												//run replacement policy algorithm
												if (policy.equals("Random")){
													Random rand = new Random();
													//random number between [0 - (associativity-1)]
													int n = rand.nextInt(assoc);
													arrCache[row][n] = eipTag;
												} else {
													//Round Robin
													int replaceCol = replacementIndex[row];
													arrCache[row][replaceCol] = eipTag;
													replacementIndex[row] = (replaceCol + 1) % assoc; //update replacement index
												}
												break;
											}
											continue;
										} else {
											System.out.println("Error in EIP cache check");
											System.exit(0);
										}
									}
									totalCycles += 2; // +2 cycles to execute instruction
									totalInstructions += 1; // adds to total number instructions for cache
								}
								break;
							



							//process both dst[6-13][15-22] and src[33-40][42-49] addresses
							//always 4 bytes read if non-zero address for both dst and src
							case 'd':
								//dstM:
								String dstAddress = null;
								StringBuilder sbDst = new StringBuilder();
								for (int j = 6; j <= 13; j++)
								{
									sbDst.append(charArray[j]);
								}
								dstAddress = sbDst.toString();
								if (!dstAddress.equals("00000000") || charArray[15] != '-')	//check if dst is actually not reading bytes
								{

								
								///  MILESTONE 3 STUFF HERE
								/// break address into two parts
								/// 	PAGE NUMBER = top 20 bits
								/// 	PAGE OFFSET = bottom 12 bits
								int dstPageNumber = getPageNumBits(dstAddress);
								int dstPageOffset = getPageOffsetBits(dstAddress);

								if (dstPageNumber >= 1048575){
									continue;
								}
								/// check the current trace files Page Table: pageTabeList[pageNumber] == ?
								int[] dstpageTable = pageTableList.get(i);
								if (dstpageTable[dstPageNumber] != -1){		//page table hit
									totalPageTableHits++;
								} else if (dstpageTable[dstPageNumber] == -1){		//page table miss
									if(freeUserPagesList.isEmpty()){
										totalPageFaults++;
										// pick from another random file first, will choose self if only trace file
										int checker = 0;
										for (int j = i; j <= traceFileList.size(); j++){
											if (j == traceFileList.size()){
												j = 0;
											}

											Tracefile tempTrace = traceFileList.get(j);
											if (checker == traceFileList.size()){
												dstpageTable[dstPageNumber] = FindFirstFreePage(pageTableList.get(i));
												break;
											}
											if (j == i){
												checker++;
												continue;
											}
											if (tempTrace.isDoneReading){
												checker++;
												continue;
											} 
											
											//pull from trace file j
											dstpageTable[dstPageNumber] = FindFirstFreePage(pageTableList.get(j));
											break;
										}
										
									} else{
										dstpageTable[dstPageNumber] = freeUserPagesList.getFirst();
										freeUserPagesList.removeFirst();
										totalPagesFromFree++;
									}
									
								}

								/// create physical address from User Page and Page Offset
								/// ex] 0x12345678
								/// 	0x12345 is the USER PAGE pulled from freeUserPagesList
								/// 	0x678 is the PAGE OFFSET(bottom 12 bits)
								long longDSTAddress = (dstpageTable[dstPageNumber] * 4096) + dstPageOffset;
								dstAddress = String.format("%08X", longDSTAddress);
							
								/// run this new Physical Address through Milestone 2 caching algorithm
								/// MILESTONE 3 STUFF ENDS HERE!!

									sumDstSrcBytes += 4;
									totalAddressesRead++;
									//dst cache check
									int dstTag = parseTagBitsToInt(toBinaryString(dstAddress), tagBits);
									int dstIndex = parseIndexBitsToInt(toBinaryString(dstAddress), tagBits, indexBits);
									int dstBlock =  parseBlockBitsToInt(toBinaryString(dstAddress), tagBits, indexBits, blockOffset);
									int dstAddBlock = addBlockRows(dstBlock, 4, blockSize);
									for (int row = dstIndex; row < (dstIndex + dstAddBlock + 1); row++){
										if (row >= arrCache.length) break; //OOB check
										int conflictCheckCount = 0;
										for (int col = 0; col < assoc; col++){
											if (col >= arrCache[row].length) break; //OOB check
										//arrCache[row][col] = dstTag;
										//every col is checked before deciding if hit/miss
	
											//checks for hit/matching tag
											if (arrCache[row][col] == dstTag){
												sumCacheHits++;
												totalCycles += 1; // cache hit; +1 cycle
												break;
											}
											//checks for compulsory miss 
											else if (arrCache[row][col] < 0){
												arrCache[row][col] = dstTag;
												sumCompulsoryMisses++;
												totalCycles += (4 * numReads); // cache miss; 4 * num reads to populate block
												break;
											}
											//checks for conflict miss
											else if (arrCache[row][col] != dstTag){
												conflictCheckCount++;
												if (conflictCheckCount == assoc){
													sumConflictMisses++;
													totalCycles += (4 * numReads); // cache miss; 4 * num reads to populate block
													//run replacement policy algorithm
													if (policy.equals("Random")){
														Random rand = new Random();
														//random number between [0 - (associativity-1)]
														int n = rand.nextInt(assoc);
														arrCache[row][n] = dstTag;
													} else {
														//Round Robin
														int replaceCol = replacementIndex[row];
														arrCache[row][replaceCol] = dstTag;
														replacementIndex[row] = (replaceCol + 1) % assoc; //update replacement index
													}
													break;
												}
												continue;
											} else {
												System.out.println("Error in dstM cache check");
												System.exit(0);
											}
										}
										totalCycles += 1; //calculate effective address; +1 cycle
									}
								}




								//srcM:
								String srcAddress = null;
								StringBuilder sbSrc = new StringBuilder();
								for (int j = 33; j <= 40; j++)
								{
									sbSrc.append(charArray[j]);
								}
								srcAddress = sbSrc.toString();
								if (!srcAddress.equals("00000000") || charArray[42] != '-')	//check if src is actually not reading bytes
								{

								///  MILESTONE 3 STUFF HERE
								/// break address into two parts
								/// 	PAGE NUMBER = top 20 bits
								/// 	PAGE OFFSET = bottom 12 bits
								int srcPageNumber = getPageNumBits(srcAddress);
								int srcPageOffset = getPageOffsetBits(srcAddress);

								if (srcPageNumber >= 1048575){
									continue;
								}
								/// check the current trace files Page Table: pageTabeList[pageNumber] == ?
								int[] srcPageTable = pageTableList.get(i);
								if (srcPageTable[srcPageNumber] != -1){		//page table hit
									totalPageTableHits++;
								} else if (srcPageTable[srcPageNumber] == -1){		//page table miss
									if(freeUserPagesList.isEmpty()){
										totalPageFaults++;
										// pick from another random file first, will choose self if only trace file
										int checker = 0;
										for (int j = i; j <= traceFileList.size(); j++){
											if (j == traceFileList.size()){
												j = 0;
											}

											Tracefile tempTrace = traceFileList.get(j);
											if (checker == traceFileList.size()){
											srcPageTable[srcPageNumber] = FindFirstFreePage(pageTableList.get(i));
												break;
											}
											if (j == i){
												checker++;
												continue;
											}
											if (tempTrace.isDoneReading){
												checker++;
												continue;
											} 
											
											//pull from trace file j
											srcPageTable[srcPageNumber] = FindFirstFreePage(pageTableList.get(j));
											break;
										}
										
									} else{
										srcPageTable[srcPageNumber] = freeUserPagesList.getFirst();
										freeUserPagesList.removeFirst();
										totalPagesFromFree++;
									}
									
								}

								/// create physical address from User Page and Page Offset
								/// ex] 0x12345678
								/// 	0x12345 is the USER PAGE pulled from freeUserPagesList
								/// 	0x678 is the PAGE OFFSET(bottom 12 bits)
								long longSRCAddress = (srcPageTable[srcPageNumber] * 4096) + srcPageOffset;
								srcAddress = String.format("%08X", longSRCAddress);
							
								/// run this new Physical Address through Milestone 2 caching algorithm
								/// MILESTONE 3 STUFF ENDS HERE!!


									sumDstSrcBytes += 4;
									totalAddressesRead++;
									//src cache check
									int srcTag = parseTagBitsToInt(toBinaryString(srcAddress), tagBits);
									int srcIndex = parseIndexBitsToInt(toBinaryString(srcAddress), tagBits, indexBits);
									int srcBlock =  parseBlockBitsToInt(toBinaryString(srcAddress), tagBits, indexBits, blockOffset);
									int srcAddBlock = addBlockRows(srcBlock, 4, blockSize);
									for (int row = srcIndex; row < (srcIndex + srcAddBlock + 1); row++){
										if (row >= arrCache.length) break; //OOB check
										int conflictCheckCount = 0;
										for (int col = 0; col < assoc; col++){
											if (col >= arrCache[row].length) break; //OOB check
										//arrCache[row][col] = srcTag;
										//every col is checked before deciding if hit/miss
	
											//checks for hit/matching tag
											if (arrCache[row][col] == srcTag){
												sumCacheHits++;
												totalCycles += 1; // cache hit; +1 cycle
												break;
											}
											//checks for compulsory miss 
											else if (arrCache[row][col] < 0){
												arrCache[row][col] = srcTag;
												sumCompulsoryMisses++;
												totalCycles += (4 * numReads); // cache miss; 4 * num reads to populate block
												break;
											}
											//checks for conflict miss
											else if (arrCache[row][col] != srcTag){
												conflictCheckCount++;
												if (conflictCheckCount == assoc){
													sumConflictMisses++;
													totalCycles += (4 * numReads); // cache miss; 4 * num reads to populate block
													//run replacement policy algorithm
													if (policy.equals("Random")){
														Random rand = new Random();
														//random number between [0 - (associativity-1)]
														int n = rand.nextInt(assoc);
														arrCache[row][n] = srcTag;
													} else {
														//Round Robin
														int replaceCol = replacementIndex[row];
														arrCache[row][replaceCol] = srcTag;
														replacementIndex[row] = (replaceCol + 1) % assoc; //update replacement index
														
													}
													break;
												}
												continue;
											} else {
												System.out.println("Error in dstM cache check");
												System.exit(0);
											}
										}
										totalCycles += 1; //calculate effective address; +1 cycle
									}
								}

							default:
								break;
						}
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		totalCacheAccesses = sumCacheHits + sumCompulsoryMisses + sumConflictMisses;
		totalCacheMisses = sumCompulsoryMisses + sumConflictMisses;
		
		//print cache simulation results
		System.out.println("\n*****CACHE SIMULATION RESULTS*****\n");
		System.out.println("Total Cache Accesses: \t\t" + totalCacheAccesses + "\t(" + totalAddressesRead + " addresses)");
		System.out.print("Instruction Bytes: \t\t" + sumInstructionBytes);
		System.out.println("\tSrcDst Bytes: " + sumDstSrcBytes);
		System.out.println("Cache Hits: \t\t\t" + sumCacheHits);
		System.out.println("Cache Misses: \t\t\t" + totalCacheMisses);
		System.out.println("--- Compulsory Misses: \t\t" + sumCompulsoryMisses);
		System.out.println("--- Conflict Misses: \t\t" + sumConflictMisses);

		int totalBlocks = cacheSize / blockSize;

		double impSizeKB = (totalBlocks*blockSize + CalculateCacheTax(assoc,tagBits,numOfIndices)) / Math.pow(2,10);
		int overheadSize = CalculateCacheTax(assoc,tagBits,numOfIndices);

		double hitRate = (double)(sumCacheHits * 100) / totalCacheAccesses;
		double missRate = (double)100 - hitRate;
		double CPI = (double) totalCycles / totalInstructions;
		double unusedKB = ((double) (((totalBlocks - sumCompulsoryMisses) * blockSize) + overheadSize) / 1024);
		//System.out.println("********   " + (cacheSize) + "   *********");
		double waste = 0.15 * unusedKB;
		double percentUnused = (unusedKB / impSizeKB) * 100;


		System.out.println("\n***** *****  CACHE HIT & MISS RATE:  ***** *****\n");
		System.out.println("Hit Rate:                       " + String.format("%.2f",hitRate) + "%");
		System.out.println("Miss Rate:                      " + String.format("%.2f",missRate) + "%");
		System.out.println("CPI:                            " + String.format("%.2f",CPI) + " Cycles/Instruction (" + totalInstructions + ")");
		System.out.printf("Unused Cache Space:             %.2f KB / %.2f KB = %.2f%%  Waste: $%.2f\n", unusedKB, impSizeKB, percentUnused, waste);
		System.out.println("Unused Cache Blocks:            " + (totalBlocks - sumCompulsoryMisses) + " / " + totalBlocks);


		System.out.println("\n***** *****  PHYSICAL MEMORY SIMULATION RESULTS:  ***** *****\n");
		System.out.println("Physical Pages Used By SYSTEM:  " + numOfPagesForSystem);
		System.out.println("Pages Available to User:        " + pagesAvailableToUser);
		System.out.println();
		totalMappedVirtualPages = totalPageTableHits + totalPagesFromFree + totalPageFaults;
		System.out.println("Virtual Pages Mapped:           " + totalMappedVirtualPages);
		System.out.println("        ----------------------");
		System.out.println("        Page Table Hits:        " + totalPageTableHits);
		System.out.println();
		System.out.println("        Pages from Free:        " + totalPagesFromFree);
		System.out.println();
		System.out.println("        Total Page Faults       " + totalPageFaults);
		System.out.println();
		System.out.println();

		long PTOverhead = (524288L * 4 * fileList.size()) / 8;
		printPageTableUsage(fileList, pageTableList, PTOverhead);


		//write Milestone results to a text file
		try (PrintStream out = new PrintStream(new FileOutputStream("Team_05_Sim_n_M#3.txt"))){
			cacheSize /= 1024;
			System.setOut(out);

			System.out.println("Cache Simulator - CS 3853 - Group #05\n");
			printTraceFiles(fileList);
			printInputParams(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
			printCacheCalcs(cacheSize, blockSize, assoc, physMem, memUsed, timeSlice, policy);
			printPhysicalMemoryCalcs(physMem, memUsed, fileList.size());

			System.out.println("\n*****CACHE SIMULATION RESULTS*****\n");
			System.out.println("Total Cache Accesses: \t\t" + totalCacheAccesses + "\t(" + totalAddressesRead + " addresses)");
			System.out.print("Instruction Bytes: \t\t" + sumInstructionBytes);
			System.out.println("\tSrcDst Bytes: " + sumDstSrcBytes);
			System.out.println("Cache Hits: \t\t\t" + sumCacheHits);
			System.out.println("Cache Misses: \t\t\t" + totalCacheMisses);
			System.out.println("--- Compulsory Misses: \t\t" + sumCompulsoryMisses);
			System.out.println("--- Conflict Misses: \t\t" + sumConflictMisses);

			System.out.println("\n***** *****  CACHE HIT & MISS RATE:  ***** *****\n");
			System.out.println("Hit Rate:                       " + String.format("%.2f",hitRate) + "%");
			System.out.println("Miss Rate:                      " + String.format("%.2f",missRate) + "%");
			System.out.println("CPI:                            " + String.format("%.2f",CPI) + " Cycles/Instruction (" + totalInstructions + ")");
			System.out.printf("Unused Cache Space:             %.2f KB / %.2f KB = %.2f%%  Waste: $%.2f\n", unusedKB, impSizeKB, percentUnused, waste);
			System.out.println("Unused Cache Blocks:            " + (totalBlocks - sumCompulsoryMisses) + " / " + totalBlocks);

			System.out.println("\n***** *****  PHYSICAL MEMORY SIMULATION RESULTS:  ***** *****\n");
			System.out.println("Physical Pages Used By SYSTEM:  " + numOfPagesForSystem);
			System.out.println("Pages Available to User:        " + pagesAvailableToUser);
			System.out.println();
			System.out.println("Virtual Pages Mapped:           " + totalMappedVirtualPages);
			System.out.println("        ----------------------");
			System.out.println("        Page Table Hits:        " + totalPageTableHits);
			System.out.println();
			System.out.println("        Pages from Free:        " + totalPagesFromFree);
			System.out.println();
			System.out.println("        Total Page Faults       " + totalPageFaults);
			System.out.println();
			System.out.println();

			printPageTableUsage(fileList, pageTableList, PTOverhead);

		} catch (IOException e) {
			e.printStackTrace();
		}

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
		public int fileLineReadPos;
		public boolean isDoneReading;
		
		public Tracefile(File filePath){
			this.filePath = filePath;
			fileLineReadPos = 1;
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
				+	"fileLineReadPos " + fileLineReadPos
				+ 	"Done Status " + Boolean.toString(isDoneReading);
		}
	}

	//convert 32-bit hex address string to binary string
	public static String toBinaryString(String str){
		long lHex = Long.parseLong(str, 16);
		String bHex = String.format("%32s", Long.toBinaryString(lHex)).replace(' ', '0');
		return bHex;
	}

	//parse tag bits from 32-bit binary address string, return as int
	public static int parseTagBitsToInt(String str, int tagBits){
		char[] charArray = new char[100];
		charArray = str.toCharArray();
		String strTag = null;
		StringBuilder sbTag = new StringBuilder();
		//tag [0 -> (tag bits-1)]
		for (int j = 0; j <= (tagBits - 1); j++)
		{
			sbTag.append(charArray[j]);
		}
		strTag = sbTag.toString();
		int iTag = Integer.parseInt(strTag, 2);	//binary string to int
		return iTag;
	}

	//parse index bits from 32-bit binary address string, return as int
	public static int parseIndexBitsToInt(String str, int tagBits, int indexBits){
		char[] charArray = new char[100];
		charArray = str.toCharArray();
		String strIndex = null;
		StringBuilder sbIndex = new StringBuilder();
		//index [tag bits -> (tag bits + index bits) - 1 ]
		for (int j = tagBits; j <= (tagBits + indexBits - 1); j++)
		{
			sbIndex.append(charArray[j]);
		}
		strIndex = sbIndex.toString();
		int iIndex = Integer.parseInt(strIndex, 2);	//binary string to int
		return iIndex;
	}

	//parse block offset bits from 32-bit binary address string, return as int
	public static int parseBlockBitsToInt(String str, int tagBits, int indexBits, int blockOffset){
		char[] charArray = new char[100];
		charArray = str.toCharArray();
		String strBlock = null;
		StringBuilder sbBlock = new StringBuilder();
		//block [(tag bits + index bits) -> (tag bits + index bits + block bits)-1
		for (int j = (tagBits + indexBits); j <= (tagBits + indexBits + blockOffset - 1); j++)
		{
			sbBlock.append(charArray[j]);
		}
		strBlock = sbBlock.toString();
		int iBlock = Integer.parseInt(strBlock, 2);	//binary string to int
		return iBlock;
	}

	//calculate how many additional blocks into the cache we need to access
	public static int addBlockRows(int block, int bytesToRead, int blockSize){
		return (int) Math.floor( (block + bytesToRead - 1) / blockSize );
	}

	public static int getPageNumBits(String str) {
		String binaryAddress = toBinaryString(str);
		binaryAddress = String.format("%32s", binaryAddress).replace(' ', '0');

		StringBuilder sbBits = new StringBuilder();

		// Loop through the first 20 bits of the binary string
		for (int i = 0; i < 20; i++) {
			sbBits.append(binaryAddress.charAt(i));
		}

		// Convert the binary string to an integer
		String strBits = sbBits.toString();
		return Integer.parseInt(strBits, 2);
	}

	public static int getPageOffsetBits(String str) {
		// Convert the address to a binary string
		String binaryAddress = toBinaryString(str);
		binaryAddress = String.format("%32s", binaryAddress).replace(' ', '0');

		StringBuilder sbBits = new StringBuilder();

		// Loop through the bits from position 20 to 31
		for (int i = 20; i < 32; i++) {
			sbBits.append(binaryAddress.charAt(i));
		}

		// Convert the binary string to an integer
		String strBits = sbBits.toString();
		return Integer.parseInt(strBits, 2);
	}

	public static void initializePTList(ArrayList<int[]> pageTableList){
        for (int[] currPageTable : pageTableList) {
            Arrays.fill(currPageTable, -1);
        }
	}

	public static int countUsedPTEntries(int[] pageTable){
		int cnt = 0;

        for (int entry : pageTable) {
            if (entry != -1) cnt++;
        }
		return cnt;
	}

	public static int FindFirstFreePage(int[] pt){
		int n = 0;
		for (int i = 0; i < pt.length; i++){
			if (pt[i] != -1){
				n = pt[i];
				pt[i] = -1;
				break;
			}
		}
		return n;		
	}


	//TODO: need to use this calculation when passing in for "overheadPT" arg:
// long totalRAMForPageTables = (numEntries * sizeOfPageTableEntry * numTraceFiles) / 8;
	public static void printPageTableUsage(ArrayList<File> fileList, ArrayList<int[]> pageTableList, long overheadPT){
		System.out.println("Page Table Usage Per Process:");
		System.out.println("------------------------------\n");

		for(int i = 0; i < fileList.size(); i++){
			File currFile = fileList.get(i);
			int usedPTEntries = countUsedPTEntries(pageTableList.get(i));
			double usedPTPercent = (double) (usedPTEntries * 100) / 524288;
			long pageTableWasted = calculateWastedSpace(524288, usedPTEntries, 4, overheadPT);

			System.out.printf("[%d] %s:\n", i, currFile.getName());
			System.out.printf("\tUsed Page Table Entries: %d (%.4f%%)\n", usedPTEntries, usedPTPercent);
			System.out.println("\tPage Table Wasted: " + pageTableWasted + " bytes\n");
		}
	}

	public static long calculateWastedSpace(int totalEntries, int usedEntries, int entrySize, long overhead){
		long totalEntriesSpace = (long) totalEntries * entrySize;
		long usedEntriesSpace = (long) usedEntries * entrySize;
		long totalSpace = totalEntriesSpace + overhead;

		return totalSpace - usedEntriesSpace;
	}

}	