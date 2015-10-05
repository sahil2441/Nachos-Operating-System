package nachos.kernel.threads;

import java.util.ArrayDeque;
import java.util.Queue;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.machine.NachosThread;

/**
 * This class provides a facility for scheduling work to be performed
 * "in the background" by "child" threads and safely communicating the results
 * back to a "parent" thread. It is loosely modeled after the AsyncTask facility
 * provided in the Android API.
 *
 * The class is provided in skeletal form. It is your job to complete the
 * implementation of this class in conformance with the Javadoc specifications
 * provided for each method. The method signatures specified below must not be
 * changed. However, the implementation will likely require additional private
 * methods as well as (private) instance variables. Also, it is essential to
 * provide proper synchronization for any data that can be accessed
 * concurrently. You may use any combination of semaphores, locks, and
 * conditions for this purpose.
 *
 * NOTE: You may NOT disable interrupts or use spinlocks.
 */
public class TaskManager {

    // Parent Thread
    public NachosThread parentThread;

    // Child Thread Queue
    private Queue<NachosThread> childThreadQueue = new ArrayDeque();

    /**
     * Initialize a new TaskManager object, and register the calling thread as
     * the "parent" thread. The parent thread is responsible (after creating at
     * least one Task object and calling its execute() method) for calling
     * processRequests() to track the completion of "child" threads and process
     * onCompletion() or onCancellation() requests on their behalf.
     */

    public TaskManager() {
    }

    /**
     * Posts a request for a Runnable to be executed by the parent thread. Such
     * a Runnable might consist of a call to <CODE>onCompletion()</CODE> or
     * <CODE>onCancellation() associated with the completion of a task being
     * performed by a child thread, or it might consist of completely unrelated
     * work (such as responding to user interface events) for the parent thread
     * to perform.
     * 
     * NOTE: This method should be safely callable by any thread.
     *
     * @param runnable
     *            Runnable to be executed by the parent thread.
     */
    public void postRequest(Runnable runnable) {
	NachosThread currentThread;
	while (!childThreadQueue.isEmpty()) {
	    currentThread = childThreadQueue.peek();
	    Debug.println('+',
		    "Current Thread in queue: " + currentThread.name);
	    allowTimeToPass();

	    // Add child thread again to scheduler to start the
	    // cancellation/completion methods

	    childThreadQueue.poll();
	    Nachos.scheduler.readyToRun(currentThread);
	}

    }

    /**
     * Called by the parent thread to process work requests posted for it. This
     * method does not return unless there are no further pending requests to be
     * processed AND all child threads have terminated. If there are no requests
     * to be processed, but some child threads are still active, then the parent
     * thread will block within this method awaiting further requests (for
     * example, those that will eventually result from the termination of the
     * currently active child threads).
     *
     * @throws IllegalStateException
     *             if the calling thread is not registered as the parent thread
     *             for this TaskManager.
     */
    public void processRequests() {

	Debug.println('+', "Entering method processRequests() from thread: "
		+ NachosThread.currentThread().name);
	NachosThread currentChild;

	// iterate the queue and start child threads
	while (!childThreadQueue.isEmpty()) {
	    currentChild = childThreadQueue.peek();
	    Debug.println('+', "Current Thread in queue: " + currentChild.name);

	    // Add child thread again to scheduler to start the
	    // cancellation/completion methods

	    childThreadQueue.poll();
	    allowTimeToPass();
	    Nachos.scheduler.yieldThread();
	}

    }

    /**
     * This method can be called to simulate "doing work". Each time it is
     * called it gives control to the NACHOS simulator so that the simulated
     * time can advance by a few "ticks".
     */
    protected void allowTimeToPass() {
	for (int i = 0; i < 10; i++) {
	    dummy.P();
	    dummy.V();
	}
    }

    /**
     * Inner class representing a task to be executed in the background by a
     * child thread. This class must be subclassed in order to override the
     * doInBackground() method and possibly also the onCompletion() and
     * onCancellation() methods.
     */
    public static class Task {

	/**
	 * If set to true then execute onCompletion method
	 */
	private boolean isCancelled = false;

	/**
	 * Cause the current task to be executed by a new child thread. In more
	 * detail, a new child thread is created, the child thread runs the
	 * doInBackground() method and upon termination of that method a request
	 * is posted for the parent thread to run either onCancellation() or
	 * onCompletion(), respectively, depending on whether or not the task
	 * was cancelled.
	 */
	public void execute() {
	    doInBackground();
	    onCompletion();
	}

	/**
	 * Flag the current Task as "cancelled", if the task has not already
	 * completed. Successful cancellation (as indicated by a return value of
	 * true) guarantees that the onCancellation() method will be executed
	 * instead of the normal onCompletion() method. This method should be
	 * safely callable by any thread.
	 *
	 * @return true if the task was successfully cancelled, otherwise false.
	 */
	public boolean cancel() {
	    this.isCancelled = true;
	    return this.isCancelled;
	}

	/**
	 * Determine whether this Task has been cancelled. This method should be
	 * safely callable by any thread.
	 *
	 * @return true if this Task has been cancelled, false otherwise.
	 */
	public boolean isCancelled() {
	    return this.isCancelled;
	}

	/**
	 * Method to be executed in the background by a child thread. Subclasses
	 * will override this with desired code. The default implementation is
	 * to do nothing and terminate immediately. Subclass implementations
	 * should call isCancelled() periodically so that this method will
	 * return promptly if this Task is cancelled. This method should not be
	 * called directly; rather, it will be called indirectly as a result of
	 * a call to the execute() method.
	 */
	protected void doInBackground() {
	}

	/**
	 * Method to be executed by the main thread upon termination of of
	 * doInBackground(). Will not be executed if the task was cancelled.
	 * This method should not be called directly; rather, it will be called
	 * indirectly as a result of a call to the execute() method.
	 */
	protected void onCompletion() {
	}

	/**
	 * Method to be executed by the main thread upon termination of
	 * doInBackground(). Will only be executed if the task was cancelled.
	 */
	protected void onCancellation() {
	}

	/**
	 * This method can be called to simulate "doing work". Each time it is
	 * called it gives control to the NACHOS simulator so that the simulated
	 * time can advance by a few "ticks".
	 */
	protected void allowTimeToPass() {
	    dummy.P();
	    dummy.V();
	}

    }

    /** Semaphore used by allowTimeToPass above. */
    private static Semaphore dummy = new Semaphore("Time waster", 1);

    /**
     * Run a demonstration of the TaskManager facility.
     */
    public static void demo() {
	Debug.println('+', "Demo begins");
	// assign parent thread
	TaskManager taskManager = new TaskManager();
	taskManager.parentThread = NachosThread.currentThread();
	Semaphore semaphore = new Semaphore("Child Semaphore", 1);

	for (int i = 0; i < 5; i++) {
	    // create child thread
	    NachosThread thread = new NachosThread("Child Thread " + i,
		    new Runnable() {

			boolean doInBackground = false;

			FindNumbersTask task = new FindNumbersTask();

			@Override
			public void run() {
			    // use semaphore to sem2 to limit execution to one
			    // child thread
			    // release sem2 at the end
			    // parent thread checks flag doInBackground from
			    // process request method
			    // If true, then it launches thread again from queue
			    // to
			    // start onCompletion or onCancellation method

			    // pause the parent thread

			    Debug.println('+',
				    "semaphore.P() executed in thread: "
					    + NachosThread
						    .currentThread().name);
			    semaphore.P();
			    if (!doInBackground) {

				task.execute();
				// finish thread after executing doInBackground
				doInBackground = true;
				// wake up parent thread
				synchronized (this) {
				    Debug.println('+',
					    "Notifying parent thread about doInBackground() task completion.");
				    notify();
				}

			    }
			    semaphore.V();
			    Debug.println('+',
				    "semaphore.V() executed in thread: "
					    + NachosThread
						    .currentThread().name);

			    // Execute onCompletion or on OnCancellation method
			    // Call to this block comes on the second run of the
			    // child thread
			    // i.e after the parent knows that child has
			    // finished
			    // doInBackground() method
			    if (task.isCancelled()) {
				task.onCompletion();
			    } else {
				task.onCancellation();
			    }
			    // yield this thread so that second child thread can
			    // execute
			    Debug.println('+', NachosThread.currentThread().name
				    + " is finishing");
			    Nachos.scheduler.finishThread();
			}
		    });
	    taskManager.childThreadQueue.add(thread);
	    Nachos.scheduler.readyToRun(thread);
	}
	taskManager.processRequests();
	Debug.println('1', "Demo terminating");
    }

}
