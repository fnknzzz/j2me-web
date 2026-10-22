/**
 * Minimal reproduction of the Asteroids game loop pattern that triggers
 * the "IDLE stuck" bug in cooperative scheduling.
 *
 * Pattern:
 *   GameThread:     post event → notifyAll(queue) → wait(producer) → sleep(30ms) → repeat
 *   DispatchThread: wait(queue) → process event → notifyAll(producer) → wait(queue) → repeat
 *   MonitorThread:  sleep(50ms) → check → loop
 *
 * The bug: after all threads enter sleep/wait, vtime must advance to
 * wake them up. If the scheduler returns IDLE, the JS run-loop only
 * pushes ~16ms/frame, causing multi-frame stalls.
 */
public class RepaintLoopTest {
    static final Object queueLock = new Object();
    static final Object producerLock = new Object();
    static volatile boolean hasEvent = false;
    static volatile boolean repaintDone = false;
    static volatile int frameCount = 0;
    static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        // Dispatch thread — like EventQueue.run()
        Thread dispatch = new Thread() {
            public void run() {
                while (running) {
                    synchronized (queueLock) {
                        while (!hasEvent) {
                            try { queueLock.wait(); } catch (InterruptedException e) { return; }
                        }
                        hasEvent = false;
                    }
                    // Process the repaint event (simulate callPaint)
                    // Then notify the producer
                    synchronized (producerLock) {
                        repaintDone = true;
                        producerLock.notifyAll();
                    }
                }
            }
        };
        dispatch.setDaemon(true);
        dispatch.start();

        // Monitor thread — like NativeEventMonitor
        Thread monitor = new Thread() {
            public void run() {
                while (running) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                    // In real code this would check for native events
                    // and notify queueLock if any. Here we just sleep.
                }
            }
        };
        monitor.setDaemon(true);
        monitor.start();

        // Game thread — like asteroids.g.run()
        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();

            // repaint() → post event
            synchronized (queueLock) {
                hasEvent = true;
                queueLock.notifyAll();
            }

            // serviceRepaints() → waitForCurrentRepaintEvents()
            synchronized (producerLock) {
                while (!repaintDone) {
                    producerLock.wait();
                }
                repaintDone = false;
            }

            frameCount++;

            // Frame rate control: sleep(max(50 - elapsed, 20))
            long elapsed = System.currentTimeMillis() - start;
            long sleepTime = Math.max(50 - elapsed, 20);
            Thread.sleep(sleepTime);
        }

        running = false;
        System.out.println("OK frames=" + frameCount);
    }
}
