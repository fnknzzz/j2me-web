public class TryCatch {
    public static void main(String[] args) {
        System.out.println("before try");
        try {
            throw new Exception("test exception");
        } catch (Exception e) {
            System.out.println("caught");
        }
        System.out.println("after catch");
    }
}
