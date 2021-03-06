// ThreadTest.java
//	Simple test class for the threads assignment.
//
//	Create two threads, and have them context switch
//	back and forth between themselves by calling yield(), 
//	to illustrate the thread system.
//
// Copyright (c) 1992-1993 The Regents of the University of California.
// Copyright (c) 1998 Rice University.
// Copyright (c) 2003 State University of New York at Stony Brook.
// All rights reserved.  See the COPYRIGHT file for copyright notice and
// limitation of liability and disclaimer of warranty provisions.

package nachos.kernel.threads.test;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.userprog.AddrSpace;
import nachos.kernel.userprog.UserThread;

/**
 * Set up a ping-pong between two threads, by forking two threads to execute
 * Runnable objects.
 * 
 * @author Thomas Anderson (UC Berkeley), original C++ version
 * @author Peter Druschel (Rice University), Java translation
 * @author Eugene W. Stark (Stony Brook University)
 */
public class ThreadTest implements Runnable {

    /** Integer identifier that indicates which thread we are. */
    private int which;

    /**
     * Initialize an instance of ThreadTest and start a new thread running on
     * it.
     *
     * @param w
     *            An integer identifying this instance of ThreadTest.
     */
    public ThreadTest(int w) {
	which = w;
	// NachosThread t = new NachosThread("Test thread " + w, this);
	// Nachos.scheduler.readyToRun(t);

	AddrSpace space = new AddrSpace();
	UserThread t = new UserThread("Thread from ThreadTest", this, space);
	Nachos.scheduler.readyToRun(t);
    }

    /**
     * Loop 5 times, yielding the CPU to another ready thread each iteration.
     */
    public void run() {
	for (int num = 0; num < 50; num++) {
	    Debug.println('+',
		    "*** thread " + which + " looped " + num + " times");
	    Nachos.consoleDriver.putChar((char) ((char) num + '\n'));
	    // System.out.println(num);
	    // Nachos.scheduler.yieldThread();
	}
	Nachos.scheduler.finishThread();
    }

    /**
     * Entry point for the test.
     */
    public static void start() {
	Debug.println('+', "Entering ThreadTest");
	new ThreadTest(1);
	new ThreadTest(2);
	new ThreadTest(3);
	new ThreadTest(4);
	new ThreadTest(5);
    }

}
