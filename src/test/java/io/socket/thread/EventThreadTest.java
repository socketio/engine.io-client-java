package io.socket.thread;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class EventThreadTest {

    @Test
    public void isCurrent() throws InterruptedException {
        final BlockingQueue<Boolean> queue = new LinkedBlockingQueue<Boolean>();

        queue.offer(EventThread.isCurrent());

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                queue.offer(EventThread.isCurrent());
            }
        });

        assertThat(queue.take(), is(false));
        assertThat(queue.take(), is(true));
    }

    @Test
    public void exec() throws InterruptedException {
        final BlockingQueue<Integer> queue = new LinkedBlockingQueue<Integer>();

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                queue.offer(0);
                EventThread.exec(new Runnable() {
                    @Override
                    public void run() {
                        queue.offer(1);
                    }
                });
                queue.offer(2);
            }
        });

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                queue.offer(3);
            }
        });

        for (int i = 0; i < 4; i++) {
            assertThat(queue.take(), is(i));
        }
    }

    @Test
    public void nextTick() throws InterruptedException {
        final BlockingQueue<Integer> queue = new LinkedBlockingQueue<Integer>();
        final Set<Thread> threads = new HashSet<Thread>();

        EventThread.exec(new Runnable() {
            @Override
            public void run() {
                threads.add(Thread.currentThread());

                queue.offer(0);
                EventThread.nextTick(new Runnable() {
                    @Override
                    public void run() {
                        threads.add(Thread.currentThread());
                        queue.offer(2);
                    }
                });
                queue.offer(1);
            }
        });

        for (int i = 0; i < 3; i++) {
            assertThat(queue.take(), is(i));
        }
        assertThat(threads.size(), is(1));
    }
}
