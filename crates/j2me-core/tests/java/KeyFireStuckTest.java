/**
 * Reproduces the IDLE-stuck bug triggered by key events (e.g. fire key).
 *
 * Architecture (matching the real MIDP framework):
 *
 *   NativeEventMonitor: sleeps 50ms, checks native event flag, posts KEY_EVENT to queue
 *   EventDispatcher:    wait(queueLock) → take event → process → if REPAINT: callPaint + notifyAll(producer)
 *                       if KEY_EVENT: synchronized(calloutLock) { keyPressed() }
 *   GameThread:         repaint() → scheduleRepaint/post → serviceRepaints/wait(producer) → sleep(20-50ms)
 *
 * The key insight from the real code:
 *   - Canvas.keyPressed and Canvas.paint both synchronize on calloutLock
 *   - CanvasLFImpl.uCallKeyPressed: synchronized(calloutLock) { canvas.keyPressed(kc); }
 *   - Display.callPaint:            synchronized(calloutLock) { canvas.paint(g); }
 *
 * Scenario: rapid key presses interleave KEY_EVENTs with REPAINT_EVENTs in the queue.
 * The dispatcher processes them in order. When it reaches KEY_EVENT, it grabs calloutLock.
 * This should not deadlock since calloutLock is not held by anyone else.
 * BUT: the extra events increase the latency of processing REPAINT_EVENT, and in
 * cooperative scheduling this can cause the game thread's wait() to time out or
 * the IDLE detection to misfire.
 */
public class KeyFireStuckTest {
    // Shared state
    static final Object queueLock = new Object();
    static final Object producerLock = new Object();
    static final Object calloutLock = new Object();
    // Simple event queue (linked list)
    static int[] eventQueue = new int[64]; // ring buffer: 0=empty, 1=REPAINT, 2=KEY
    static int qHead = 0, qTail = 0, qSize = 0;
    static volatile boolean repaintDone = false;
    static volatile int repaintUseId = 0;
    static volatile int frameCount = 0;
    static volatile boolean running = true;
    static volatile boolean hasNativeKeyEvent = false; // simulates Rust native_event_queue
    static volatile int lastKeyCode = 0; // what keyPressed sees

    static void enqueue(int eventType) {
        eventQueue[qTail] = eventType;
        qTail = (qTail + 1) % eventQueue.length;
        qSize++;
    }

    static int dequeue() {
        if (qSize == 0) return 0;
        int e = eventQueue[qHead];
        qHead = (qHead + 1) % eventQueue.length;
        qSize--;
        return e;
    }

    public static void main(String[] args) throws Exception {
        // --- Event Dispatch Thread (like EventQueue.run()) ---
        Thread dispatcher = new Thread() {
            public void run() {
                while (running) {
                    int eventType;
                    synchronized (queueLock) {
                        while (qSize == 0) {
                            try { queueLock.wait(); } catch (InterruptedException e) { return; }
                        }
                        eventType = dequeue();
                    }

                    if (eventType == 1) {
                        // REPAINT_EVENT: callPaint → canvas.paint()
                        synchronized (calloutLock) {
                            // simulate paint work
                        }
                        synchronized (producerLock) {
                            repaintUseId++;
                            producerLock.notifyAll();
                        }
                    } else if (eventType == 2) {
                        // KEY_EVENT: uCallKeyPressed → canvas.keyPressed()
                        synchronized (calloutLock) {
                            // simulate keyPressed - just read the code, very fast
                            lastKeyCode = 53; // '5' = fire key
                        }
                    }
                }
            }
        };
        dispatcher.setDaemon(true);
        dispatcher.start();

        // --- NativeEventMonitor Thread ---
        Thread nativeMonitor = new Thread() {
            public void run() {
                while (running) {
                    try { Thread.sleep(50); } catch (InterruptedException e) { return; }
                    if (hasNativeKeyEvent) {
                        hasNativeKeyEvent = false;
                        synchronized (queueLock) {
                            enqueue(2); // KEY_EVENT
                            queueLock.notifyAll();
                        }
                    }
                }
            }
        };
        nativeMonitor.setDaemon(true);
        nativeMonitor.start();

        // --- Key injection thread (simulates rapid fire key presses from JS) ---
        Thread keyInjector = new Thread() {
            public void run() {
                // Wait a bit for game to start, then inject keys rapidly
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
                for (int k = 0; k < 20 && running; k++) {
                    hasNativeKeyEvent = true;
                    try { Thread.sleep(30); } catch (InterruptedException e) { return; }
                }
            }
        };
        keyInjector.setDaemon(true);
        keyInjector.start();

        // --- Game Thread (like asteroids.g.run()) ---
        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();

            // repaint() → scheduleRepaint → post REPAINT_EVENT
            synchronized (queueLock) {
                enqueue(1); // REPAINT_EVENT
                queueLock.notifyAll();
            }

            // serviceRepaints() → waitForCurrentRepaintEvents()
            int waitForId = repaintUseId;
            synchronized (producerLock) {
                while (repaintUseId == waitForId) {
                    producerLock.wait();
                }
            }

            frameCount++;

            // Frame rate control
            long elapsed = System.currentTimeMillis() - start;
            long sleepTime = Math.max(50 - elapsed, 20);
            Thread.sleep(sleepTime);
        }

        running = false;
        System.out.println("OK frames=" + frameCount);
    }
}
