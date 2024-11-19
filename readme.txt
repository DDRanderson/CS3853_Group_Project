Instructions to Run Java Script
-------------------------------

type "cmd" in address bar to open up console to this directory
type "javac [filename]" to compile the program
run the program by typing "java [filename]"

pass arguments on the command line with -[option]
	example:
		java test -s 8
		
copy paste this to command line for test#1:
	java driver.java -s 512 -b 16 -a 4 -r rr -p 1024 -n 100 –u 75 -f Trace1.trc -f Trace2_4Evaluation.trc –f Corruption.trc
	
	test#2:
	java driver.java -s 1024 -b 32 -a 2 -r rr -p 2048 -n 100 –u 50 -f Trace1.trc -f Trace2_4Evaluation.trc –f Corruption.trc
	
	test#3:
	java driver.java -s 8198 -b 64 -a 16 -r rr -p 4096 -n 100 –u 25 -f Trace1.trc -f Trace2_4Evaluation.trc –f Corruption.trc
	
copy paste this to command line for test#2:	
**NOTE: make sure .trc files are inside the same directory as the driver.java!
	java driver.java -s 512 -b 16 -a 4 -r rr -p 1024 -n 100 –u 75 -f A-9_new_1.5.pdf.trc
	
	java driver.java -s 8 -b 8 -a 1 -r rnd -p 1024 -n 50 –u 75 -f A-9_new_1.5.pdf.trc
	
	java driver.java -s 8192 -b 64 -a 16 -r rnd -p 4096 -n 200 –u 100 -f A-9_new_1.5.pdf.trc