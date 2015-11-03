// ConsoleDriver.java
//
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and 
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.devices;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.threads.Lock;
import nachos.kernel.threads.Semaphore;
import nachos.machine.Console;
import nachos.machine.InterruptHandler;

/**
 * This class provides for the initialization of the NACHOS console, and gives
 * NACHOS user programs a capability of outputting to the console. This driver
 * does not perform any input or output buffering, so a thread performing output
 * must block waiting for each individual character to be printed, and there are
 * no input-editing (backspace, delete, and the like) performed on input typed
 * at the keyboard.
 * 
 * Students will rewrite this into a full-fledged interrupt-driven driver that
 * provides efficient, thread-safe operation, along with echoing and
 * input-editing features.
 * 
 * @author Eugene W. Stark
 */
public class ConsoleDriver {

    /** Raw console device. */
    private Console console;

    /** Lock used to ensure at most one thread trying to input at a time. */
    private Lock inputLock;

    /** Lock used to ensure at most one thread trying to output at a time. */
    private Lock outputLock;

    /** Semaphore used to indicate that an input character is available. */
    private Semaphore charAvail = new Semaphore("Console char avail", 0);

    /**
     * Semaphore used to indicate that output is ready to accept a new
     * character.
     */
    private Semaphore outputDone = new Semaphore("Console output done", 1);

    /** Interrupt handler used for console keyboard interrupts. */
    private InterruptHandler inputHandler;

    /** Interrupt handler used for console output interrupts. */
    private InterruptHandler outputHandler;

    /**
     * This buffer holds the character to be printed to console till it becomes
     * full
     */
    private char[] buffer;

    /**
     * Initialize the driver and the underlying physical device.
     * 
     * @param console
     *            The console device to be managed.
     */
    public ConsoleDriver(Console console) {
	inputLock = new Lock("console driver input lock");
	outputLock = new Lock("console driver output lock");
	setBuffer(new char[10]);
	this.console = console;
	// Delay setting the interrupt handlers until first use.
    }

    /**
     * Create and set the keyboard interrupt handler, if one has not already
     * been set.
     */
    private void ensureInputHandler() {
	if (inputHandler == null) {
	    inputHandler = new InputHandler();
	    console.setInputHandler(inputHandler);
	}
    }

    /**
     * Create and set the output interrupt handler, if one has not already been
     * set.
     */
    private void ensureOutputHandler() {
	if (outputHandler == null) {
	    outputHandler = new OutputHandler();
	    console.setOutputHandler(outputHandler);
	}
    }

    /**
     * Wait for a character to be available from the console and then return the
     * character. See method prepareOutputBufferForReadSysCall() that is called
     * with each read syscall and prepares the output buffer
     */
    public char getChar() {

	inputLock.acquire();
	ensureInputHandler();
	charAvail.P();
	Debug.ASSERT(console.isInputAvail());
	char ch = console.getChar();
	inputLock.release();
	return ch;
    }

    /**
     * Print a single character on the console. If the console is already busy
     * outputting a character, then wait for it to finish before attempting to
     * output the new character. A lock is employed to ensure that at most one
     * thread at a time will attempt to print.
     *
     * @param ch
     *            The character to be printed.
     */
    public void putChar(char ch) {

	int index = getIndexOfBuffer();
	if (index == buffer.length) {
	    // buffer is full. Print the characters and then insert the given
	    // char 'ch'
	    printBufferToConsole();
	}
	// now insert char into buffer
	buffer[getIndexOfBuffer()] = ch;
    }

    private void printBufferToConsole() {
	outputLock.acquire();
	ensureOutputHandler();
	outputDone.P();
	Debug.ASSERT(!console.isOutputBusy());

	// print char at index=0 and shift rest
	int i = 0;
	char c1 = '\u0000';
	console.putChar(buffer[i]);

	while (i < buffer.length - 1) {
	    buffer[i] = buffer[i + 1];
	    i++;
	}
	buffer[i] = c1;
	outputLock.release();
    }

    private int getIndexOfBuffer() {
	int i = 0;
	char c1 = '\u0000';
	while (i < buffer.length) {
	    if (buffer[i] == c1) {
		return i;
	    }
	    i++;
	}
	return i;
    }

    /**
     * Stop the console device. This removes the interrupt handlers, which
     * otherwise prevent the Nachos simulation from terminating automatically.
     */
    public void stop() {
	inputLock.acquire();
	console.setInputHandler(null);
	inputLock.release();
	outputLock.acquire();
	console.setOutputHandler(null);
	outputLock.release();
    }

    public char[] getBuffer() {
	return buffer;
    }

    public void setBuffer(char[] buffer) {
	this.buffer = buffer;
    }

    /**
     * Interrupt handler for the input (keyboard) half of the console.
     */
    private class InputHandler implements InterruptHandler {

	@Override
	public void handleInterrupt() {
	    charAvail.V();
	}

    }

    /**
     * Interrupt handler for the output (screen) half of the console.
     */
    private class OutputHandler implements InterruptHandler {

	@Override
	public void handleInterrupt() {
	    outputDone.V();
	}

    }

    /**
     * This method print the entire line to console whenever \n or \r is
     * pressed.
     * 
     * @param currentLineStartingIndex
     */
    public void printToConsole(char ch) {
	outputLock.acquire();
	ensureOutputHandler();
	outputDone.P();
	Debug.ASSERT(!console.isOutputBusy());
	console.putChar(ch);
	outputLock.release();
    }

    public void printOuputBufferToConsole(char[] outputBuffer) {

	// initially shift the number of spaces based on the last input line
	shiftSpacesToLeft(getNUmberOfSpaces(outputBuffer, outputBuffer.length));

	for (int i = 0; i < outputBuffer.length; i++) {
	    if (outputBuffer[i] == '\n') {
		// shift some spaces to left
		shiftSpacesToLeft(getNUmberOfSpaces(outputBuffer, i));
	    }
	    printToConsole(outputBuffer[i]);
	}
	// terminate the console here
	Debug.println('+', "ConsoleTest: quitting");
	Nachos.consoleDriver.stop();
    }

    private int getNUmberOfSpaces(char[] outputBuffer, int start) {
	int index = start - 1;
	while (index >= 0) {
	    if (index == 0 || outputBuffer[index] == '\n'
		    || outputBuffer[index] == '\r') {
		return start - index;
	    }
	    index--;
	}
	return index < 0 ? 0 : index;
    }

    /**
     * This method is called with each read syscall and prepares the output
     * buffer for further printing on Nachos Console.
     * 
     * @param size
     * @return
     */
    public char[] prepareOutputBufferForReadSysCall(int size) {
	char[] outputBuffer = new char[size];
	int index = 0;
	int currentLineStartingIndex = 0;
	while (index < size) {
	    char ch = getChar();

	    // process the input char 'ch'
	    if (ch >= 32 && ch <= 126) {
		outputBuffer[index] = ch;
		printToConsole(ch);
		index++;
	    } else if (ch == '\n' || ch == '\r') {
		printToConsole(ch);
		if (ch == '\n') {
		    outputBuffer[index] = '\n';
		} else {
		    outputBuffer[index] = '\r';
		}
		shiftSpacesToLeft(index - currentLineStartingIndex);
		currentLineStartingIndex = ++index;
	    } else if (ch == '\b') {
		// to implement backspace
		// go back one position, print one space, again go back one
		// position
		char c1 = '\u0000';
		printToConsole(ch);
		printToConsole((char) 32);
		printToConsole(ch);
		outputBuffer[index] = c1;
		index--;
		if (index < 0) {
		    index++;
		}
	    } else if (ch == (char) 21) {
		// Erase the entire current line and reposition the cursor
		setCurrentLineToNull(currentLineStartingIndex, index,
			outputBuffer);
		index -= (index - currentLineStartingIndex);
		currentLineStartingIndex = index;
	    } else if (ch == (char) 18) {
		// Erase the entire line and retype it
		eraseCurrentLine(index - currentLineStartingIndex);
		printCurrentLine(currentLineStartingIndex, index, outputBuffer);
	    }
	}
	return outputBuffer;
    }

    private void printCurrentLine(int currentLineStartingIndex, int lastIndex,
	    char[] outputBuffer) {
	for (int i = currentLineStartingIndex; i < lastIndex; i++) {
	    printToConsole(outputBuffer[i]);
	}

    }

    private void setCurrentLineToNull(int currentLineStartingIndex, int index,
	    char[] outputBuffer) {
	char c1 = '\u0000';
	for (int i = currentLineStartingIndex; i < index; i++) {
	    printToConsole('\b');
	    printToConsole((char) 32);
	    printToConsole('\b');
	    outputBuffer[i] = c1;
	}
    }

    private void eraseCurrentLine(int n) {
	for (int i = 0; i < n; i++) {
	    printToConsole('\b');
	    printToConsole((char) 32);
	    printToConsole('\b');
	}
    }

    private void shiftSpacesToLeft(int index) {
	for (int i = 0; i < index; i++) {
	    printToConsole('\b');
	}
    }
}
