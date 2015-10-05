package nachos.kernel.threads;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.machine.NachosThread;

/**
 * A <CODE>CyclicBarrier</CODE> is an object that allows a set of threads to all
 * wait for each other to reach a common barrier point. To find out more, read
 * <A HREF=
 * "http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/CyclicBarrier.html">
 * the documentation</A> for the Java API class
 * <CODE>java.util.concurrent.CyclicBarrier</CODE>.
 *
 * The class is provided in skeletal form. It is your job to complete the
 * implementation of this class in conformance with the Javadoc specifications
 * provided for each method. The method signatures specified below must not be
 * changed. However, the implementation will likely require additional private
 * methods as well as (private) instance variables. Also, it is essential to
 * provide proper synchronization for any data that can be accessed
 * concurrently. You may use ONLY semaphores for this purpose. You may NOT
 * disable interrupts or use locks or spinlocks.
 *
 * NOTE: The skeleton below reflects some simplifications over the version of
 * this class in the Java API.
 */
public class CyclicBarrier {

    /** The number of parties */
    private int parties;

    /**
     * Check if barrier is broken
     */
    private boolean isBarrierBroken;

    /**
     * Number of parties still waiting. Counts down from parties to 0 on each
     * generation. It is reset to parties on each new generation or when broken.
     */
    private int count;

    /** Class of exceptions thrown in case of a broken barrier. */
    public static class BrokenBarrierException extends Exception {

	private static final long serialVersionUID = -7122487149233672317L;

	// Default Constructor
	public BrokenBarrierException() {
	}

	/**
	 * Constructor with proper message
	 * 
	 * @param message
	 */
	public BrokenBarrierException(String message) {
	    super(message);
	}
    }

    /**
     * Creates a new CyclicBarrier that will trip when the given number of
     * parties (threads) are waiting upon it, and does not perform a predefined
     * action when the barrier is tripped.
     *
     * @param parties
     *            The number of parties.
     */
    public CyclicBarrier(int parties) {
	this.parties = parties;
	count = parties;
    }

    /**
     * Creates a new CyclicBarrier that will trip when the given number of
     * parties (threads) are waiting upon it, and which will execute the given
     * barrier action when the barrier is tripped, performed by the last thread
     * entering the barrier.
     *
     * @param parties
     *            The number of parties.
     * @param barrierAction
     *            An action to be executed when the barrier is tripped,
     *            performed by the last thread entering the barrier.
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
	if (parties <= 0) {
	    throw new IllegalArgumentException();
	}
	this.parties = parties;
    }

    /**
     * Waits until all parties have invoked await on this barrier. If the
     * current thread is not the last to arrive then it blocks until either the
     * last thread arrives or some other thread invokes reset() on this barrier.
     *
     * @return The arrival index of the current thread, where index getParties()
     *         - 1 indicates the first to arrive and zero indicates the last to
     *         arrive.
     * @throws BrokenBarrierException
     *             in case this barrier is broken.
     */
    public int await() throws BrokenBarrierException {
	sem1.P();
	if (isBroken()) {
	    throw new BrokenBarrierException();
	}
	count--;

	// Check if count==0 If yes then break the barrier and release threads
	if (count == 0) {
	    // Thread Released

	    sem2.V();
	    Debug.println('+', "Thread release using sem2.V()");
	}
	sem1.V();
	sem2.P();

	// Thread Released
	sem2.V();
	Debug.println('+', "Thread: " + NachosThread.currentThread().name
		+ " back to execution");
	return count;
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * 
     * @return the number of parties currently waiting at the barrier.
     */
    public int getNumberWaiting() {
	return parties - count + 1;
    }

    /**
     * Returns the number of parties required to trip this barrier.
     * 
     * @return the number of parties required to trip this barrier.
     */
    public int getParties() {
	return parties;
    }

    /**
     * Queries if this barrier is in a broken state.
     * 
     * @return true if this barrier was reset while one or more threads were
     *         blocked in await(), false otherwise.
     */

    public boolean isBroken() {
	return isBarrierBroken;
    }

    /**
     * Resets the barrier to its initial state.
     */
    public void reset() {
	// set the value of the flag isBroken=true
	// this flag is checked in the await method
	isBarrierBroken = true;
    }

    /**
     * This method can be called to simulate "doing work". Each time it is
     * called it gives control to the NACHOS simulator so that the simulated
     * time can advance by a few "ticks".
     */
    public static void allowTimeToPass() {
	sem1.P();
	sem1.V();
    }

    /** Semaphore used by allowTimeToPass above. */
    private static Semaphore sem1 = new Semaphore("Time waster", 3);

    /**
     * To keep hold
     */
    private static Semaphore sem2 = new Semaphore("Sem 2", 0);

    /**
     * Run a demonstration of the CyclicBarrier facility.
     * 
     * @param args
     *            Arguments from the "command line" that can be used to modify
     *            features of the demonstration such as the number of parties,
     *            the amount of "work" performed by each thread, etc.
     *
     *            IMPORTANT: Be sure to test your demo with the "-rs xxxxx"
     *            command-line option passed to NACHOS (the xxxxx should be
     *            replaced by an integer to be used as the seed for NACHOS'
     *            pseudorandom number generator). If you fail to include this
     *            option, then a thread that has been started will always run to
     *            completion unless it explicitly yields the CPU. This will
     *            result in the same (very uninteresting) execution each time
     *            NACHOS is run, which will not be a very good test of your
     *            code.
     * @param noOfThreads
     */

    public static void demo() {
	// Very simple example of the intended use of the CyclicBarrier
	// facility: you should replace this code with something much
	// more interesting.
	final CyclicBarrier barrier = new CyclicBarrier(2);
	Debug.println('+', "Demo starting");
	for (int i = 0; i < 5; i++) {
	    NachosThread thread = new NachosThread("Worker thread " + i,
		    new Runnable() {
			public void run() {
			    Debug.println('+',
				    "Thread "
					    + NachosThread.currentThread().name
					    + " is starting");
			    for (int j = 0; j < 3; j++) {
				Debug.println('+',
					"Thread "
						+ NachosThread
							.currentThread().name
						+ " beginning phase " + j);
				for (int k = 0; k < 5; k++) {
				    Debug.println('+',
					    "Thread "
						    + NachosThread
							    .currentThread().name
						    + " is working");
				    CyclicBarrier.allowTimeToPass(); // Do
								     // "work".
				}
				Debug.println('+',
					"Thread "
						+ NachosThread
							.currentThread().name
						+ " is waiting at the barrier");

				try {
				    barrier.await();
				} catch (BrokenBarrierException e) {
				    Debug.println('+',
					    "Barrier broken from Thread: "
						    + NachosThread
							    .currentThread().name);
				}
				Debug.println('+',
					"Thread "
						+ NachosThread
							.currentThread().name
						+ " has finished phase " + j);
			    }
			    Debug.println('+',
				    "Thread "
					    + NachosThread.currentThread().name
					    + " is terminating");
			    System.out.println();
			    Nachos.scheduler.finishThread();
			}
		    });
	    Nachos.scheduler.readyToRun(thread);
	}
	Debug.println('+', "Demo terminating");
    }
}
