package com.github.nkzawa.thread;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


/**
 * The thread for event loop. All non-background tasks run within this thread.
 */
public class EventThread extends Thread {

    private static final ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            thread = new EventThread(runnable);
            return thread;
        }
    });

    private static volatile EventThread thread;


    private EventThread(Runnable runnable) {
        super(runnable);
    }

    /**
     * check if the current thread is EventThread.
     *
     * @return true if the current thread is EventThread.
     */
    public static boolean isCurrent() {
        return currentThread() == thread;
    }

    /**
     * Executes a task in EventThread.
     *
     * @param task
     */
    public static void exec(Runnable task) {
        if (isCurrent()) {
            task.run();
        } else {
            service.execute(task);
        }
    }

    /**
     * Executes a task on the next loop in EventThread.
     *
     * @param task
     */
    public static void nextTick(Runnable task) {
        service.execute(task);
    }

}
