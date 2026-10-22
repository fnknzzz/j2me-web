/**
 * Test that invokeinterface correctly dispatches to overridden methods
 * in subclasses that inherit (but don't directly implement) an interface.
 *
 * Scenario: Parent implements Runnable, Child extends Parent and overrides run().
 * invokeinterface Runnable.run() on a Child instance must dispatch to Child.run().
 */
public class ItableInheritTest {

    static class Parent implements Runnable {
        public void run() {
            System.out.println("PARENT");
        }
    }

    static class Child extends Parent {
        public void run() {
            System.out.println("CHILD");
        }
    }

    static class GrandChild extends Child {
        public void run() {
            System.out.println("GRANDCHILD");
        }
    }

    public static void main(String[] args) {
        Runnable r1 = new Parent();
        Runnable r2 = new Child();
        Runnable r3 = new GrandChild();

        // invokeinterface Runnable.run()
        r1.run();
        r2.run();
        r3.run();

        // Also test via a helper that takes Runnable (forces invokeinterface)
        invoke(new Parent());
        invoke(new Child());
        invoke(new GrandChild());
    }

    static void invoke(Runnable r) {
        r.run();
    }
}
