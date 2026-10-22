/**
 * Test idle_delay behavior for Thread.sleep.
 *
 * This class enters Thread.sleep(1000) immediately.
 * The test verifies that idle_delay returns a value close to 1000
 * (not 0, which would cause busy-polling).
 */
public class IdleDelayTest {
    public static void main(String[] args) throws Exception {
        // Sleep for 1000ms — when the VM goes IDLE, idle_delay should return ~1000
        Thread.sleep(1000);
        System.out.println("OK sleep done");
    }
}
