public class SaveStateTest {
    static int counter = 0;

    public static void main(String[] args) {
        for (int i = 1; i <= 5; i++) {
            counter += i;
            System.out.println("step" + i + "=" + counter);
        }
        System.out.println("done=" + counter);
    }
}
