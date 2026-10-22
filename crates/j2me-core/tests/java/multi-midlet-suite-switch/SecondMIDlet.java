import javax.microedition.midlet.MIDlet;

public final class SecondMIDlet extends MIDlet {
    private static volatile boolean workerRunning = true;

    protected void startApp() {
        System.out.println("SecondMIDlet start value=" + SharedState.value + " initCount=" + SharedState.initCount);
        if (SharedState.value == 0 && SharedState.initCount == 1) {
            System.out.println("SUITE_SWITCH_STATIC_CLEAN");
        } else {
            System.out.println("SUITE_SWITCH_STATIC_DIRTY");
        }
        Thread worker = new Thread(new Runnable() {
            public void run() {
                int ticks = 0;
                while (workerRunning) {
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                    }
                    System.out.println("SECOND_WORKER_TICK " + (++ticks));
                }
            }
        });
        worker.start();
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {
        workerRunning = false;
        System.out.println("SecondMIDlet destroy");
    }
}
