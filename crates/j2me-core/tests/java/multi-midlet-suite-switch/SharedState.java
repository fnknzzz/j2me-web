public final class SharedState {
    public static int value = init();
    public static int initCount;

    private static int init() {
        initCount++;
        System.out.println("SharedState <clinit> initCount=" + initCount);
        return 0;
    }
}
