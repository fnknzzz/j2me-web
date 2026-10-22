/**
 * Test that INVOKEVIRTUAL correctly dispatches interface methods
 * through abstract classes that don't define the method themselves.
 *
 * Scenario: Interface I defines foo(). Abstract class A implements I but
 * does NOT override foo(). Concrete class B extends A and implements foo().
 * Calling a.foo() via INVOKEVIRTUAL (through an A-typed reference) must
 * correctly dispatch to B.foo().
 *
 * Also tests that multiple concrete subclasses of the same abstract class
 * share the same vtable index for the interface method.
 */
public class AbstractVtableTest {

    interface Callable {
        int call(int x);
    }

    interface Named {
        String name();
    }

    // Abstract class implements both interfaces but defines neither method
    static abstract class AbstractBase implements Callable, Named {
        // intentionally empty — no override of call() or name()
    }

    static class Doubler extends AbstractBase {
        public int call(int x) { return x * 2; }
        public String name() { return "Doubler"; }
    }

    static class Tripler extends AbstractBase {
        public int call(int x) { return x * 3; }
        public String name() { return "Tripler"; }
    }

    // Second level: abstract class that partially implements
    static abstract class PartialBase implements Callable, Named {
        public String name() { return "Partial"; }
        // call() left abstract
    }

    static class Squarer extends PartialBase {
        public int call(int x) { return x * x; }
    }

    public static void main(String[] args) {
        // Test via abstract-typed references (forces INVOKEVIRTUAL on AbstractBase)
        AbstractBase b1 = new Doubler();
        AbstractBase b2 = new Tripler();
        System.out.println(b1.call(5));   // 10
        System.out.println(b2.call(5));   // 15
        System.out.println(b1.name());    // Doubler
        System.out.println(b2.name());    // Tripler

        // Test via interface-typed references (INVOKEINTERFACE)
        Callable c1 = new Doubler();
        Callable c2 = new Tripler();
        System.out.println(c1.call(7));   // 14
        System.out.println(c2.call(7));   // 21

        // Test partial implementation
        PartialBase p = new Squarer();
        System.out.println(p.call(4));    // 16
        System.out.println(p.name());     // Partial

        // Test via helper that takes AbstractBase (INVOKEVIRTUAL)
        testVirtual(new Doubler(), 3);    // 6
        testVirtual(new Tripler(), 3);    // 9
    }

    static void testVirtual(AbstractBase base, int x) {
        System.out.println(base.call(x));
    }
}
