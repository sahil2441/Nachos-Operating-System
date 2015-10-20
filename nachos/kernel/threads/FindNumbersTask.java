package nachos.kernel.threads;

import nachos.Debug;
import nachos.kernel.Nachos;
import nachos.kernel.threads.TaskManager.Task;
import nachos.machine.NachosThread;

public class FindNumbersTask extends Task {

    public FindNumbersTask() {
    }

    @Override
    public void execute() {
	doInBackground();
    }

    @Override
    protected void doInBackground() {
	Debug.println('+', "Thread " + NachosThread.currentThread().name
		+ " is starting task ");
	for (int j = 0; j < 10; j++) {
	    allowTimeToPass(); // Do "work".
	    Debug.println('+', "Thread " + NachosThread.currentThread().name
		    + " is working on task ");
	}
	Debug.println('+', "Thread " + NachosThread.currentThread().name
		+ " is finishing task ");
    }

    @Override
    protected void onCompletion() {
	Debug.println('+', "Thread " + NachosThread.currentThread().name
		+ " is executing onCompletion() " + " for task ");
	// finish thread
	Debug.println('+',
		NachosThread.currentThread().name + " is finishing.");
	Nachos.scheduler.finishThread();

    }

    @Override
    protected void onCancellation() {
	Debug.println('+', "Thread " + Thread.currentThread().getName()
		+ " is executing onCompletion() " + " for task ");
	// finish thread
	Debug.println('+',
		NachosThread.currentThread().name + " is finishing.");
	Nachos.scheduler.finishThread();

    }
}
