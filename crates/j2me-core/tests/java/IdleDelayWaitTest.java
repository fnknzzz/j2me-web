/**
 * Test idle_delay behavior for indefinite Object.wait().
 *
 * Main thread starts a worker then does Object.wait() with no timeout.
 * The test verifies that idle_delay returns u32::MAX (not 0)
 * when all threads are in indefinite wait (no timed sleepers).
 */
public class IdleDelayWaitTest {
    static final Object lock = new Object();
    static volatile boolean done = false;

    public static void main(String[] args) throws Exception {
        // Worker thread that will notify after being woken externally
        Thread worker = new Thread() {
            public void run() {
                synchronized (lock) {
                    while (!done) {
                        try { lock.wait(); } catch (InterruptedException e) { return; }
                    }
                }
            }
        };
        worker.setDaemon(true);
        worker.start();

        // Main thread also waits indefinitely
        // Both threads in indefinite wait → idle_delay should be u32::MAX
        synchronized (lock) {
            lock.wait();
        }
        System.out.println("OK wait done");
    }
}
