public class ThreadJoinTest {

    static class Worker extends Thread {
        String result = "";
        private int id;
        Worker(int id) { this.id = id; }
        public void run() {
            int sum = 0;
            for (int i = 0; i < 200; i++) sum += i;
            result = "done-" + id;
        }
    }

    public static void main(String[] args) throws Exception {
        // Test 1: basic join — caller must not see result before join() returns
        Worker w1 = new Worker(1);
        w1.start();
        w1.join();
        if (!"done-1".equals(w1.result)) {
            System.out.println("FAIL basic join: result=" + w1.result);
        } else {
            System.out.println("PASS basic join");
        }

        // Test 2: join on already-finished thread must return immediately
        Worker w2 = new Worker(2);
        w2.start();
        // Spin until the worker has set the flag
        for (int i = 0; i < 1000000; i++) {
            if ("done-2".equals(w2.result)) break;
        }
        w2.join(); // should not block — thread already terminated
        System.out.println("PASS join already finished");

        // Test 3: sequential start+join enforces ordering
        Worker a = new Worker(10);
        Worker b = new Worker(20);
        Worker c = new Worker(30);
        a.start(); a.join();
        b.start(); b.join();
        c.start(); c.join();
        if ("done-10".equals(a.result) && "done-20".equals(b.result) && "done-30".equals(c.result)) {
            System.out.println("PASS join ordering");
        } else {
            System.out.println("FAIL join ordering: " + a.result + " " + b.result + " " + c.result);
        }
    }
}
