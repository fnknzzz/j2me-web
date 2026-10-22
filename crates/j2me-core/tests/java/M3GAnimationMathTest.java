import javax.microedition.m3g.AnimationController;
import javax.microedition.m3g.AnimationTrack;
import javax.microedition.m3g.Group;
import javax.microedition.m3g.KeyframeSequence;

public class M3GAnimationMathTest {
    private static final float EPS = 0.001f;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== M3G Animation Math Test ===");
        testLinearTranslation();
        testStepVisibilityAndValidity();
        testSplineTranslation();
        testSquadOrientation();
        testControllerActivation();
        testWeightedTrackAccumulation();
        testRepeatModesAfterDuration();
        testControllerLargeWorldTimePrecision();
        System.out.println("PASSED: " + passed + " FAILED: " + failed);
        System.out.println("=== Tests Complete ===");
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static boolean approx(float a, float b) {
        return Math.abs(a - b) < EPS;
    }

    private static void testLinearTranslation() {
        Group group = new Group();
        KeyframeSequence sequence = new KeyframeSequence(2, 3, KeyframeSequence.LINEAR);
        sequence.setDuration(1000);
        sequence.setKeyframe(0, 0, new float[]{0.0f, 10.0f, -5.0f});
        sequence.setKeyframe(1, 1000, new float[]{100.0f, 30.0f, 15.0f});
        addTrack(group, sequence, AnimationTrack.TRANSLATION, new AnimationController());

        int validity = group.animate(500);
        float[] translation = new float[3];
        group.getTranslation(translation);
        check("linear x", approx(translation[0], 50.0f));
        check("linear y", approx(translation[1], 20.0f));
        check("linear z", approx(translation[2], 5.0f));
        check("linear validity", validity == 1);
    }

    private static void testStepVisibilityAndValidity() {
        Group group = new Group();
        KeyframeSequence sequence = new KeyframeSequence(3, 1, KeyframeSequence.STEP);
        sequence.setDuration(1000);
        sequence.setKeyframe(0, 0, new float[]{1.0f});
        sequence.setKeyframe(1, 400, new float[]{0.0f});
        sequence.setKeyframe(2, 1000, new float[]{1.0f});
        addTrack(group, sequence, AnimationTrack.VISIBILITY, new AnimationController());

        int validity = group.animate(250);
        check("step visible before key", group.isRenderingEnabled());
        check("step validity before next key", validity == 150);

        validity = group.animate(400);
        check("step hidden on key", !group.isRenderingEnabled());
        check("step validity after key", validity == 600);
    }

    private static void testSplineTranslation() {
        Group group = new Group();
        KeyframeSequence sequence = new KeyframeSequence(3, 3, KeyframeSequence.SPLINE);
        sequence.setDuration(1000);
        sequence.setKeyframe(0, 0, new float[]{0.0f, 0.0f, 0.0f});
        sequence.setKeyframe(1, 500, new float[]{100.0f, 0.0f, 0.0f});
        sequence.setKeyframe(2, 1000, new float[]{0.0f, 0.0f, 0.0f});
        addTrack(group, sequence, AnimationTrack.TRANSLATION, new AnimationController());

        float[] translation = new float[3];
        group.animate(250);
        group.getTranslation(translation);
        check("spline first half hermite", approx(translation[0], 50.0f));

        group.animate(750);
        group.getTranslation(translation);
        check("spline second half hermite", approx(translation[0], 50.0f));
    }

    private static void testSquadOrientation() {
        Group group = new Group();
        KeyframeSequence sequence = new KeyframeSequence(4, 4, KeyframeSequence.SQUAD);
        sequence.setDuration(1000);
        sequence.setKeyframe(0, 0, quatZ(0.0f));
        sequence.setKeyframe(1, 333, quatZ(45.0f));
        sequence.setKeyframe(2, 666, quatZ(120.0f));
        sequence.setKeyframe(3, 1000, quatZ(180.0f));
        addTrack(group, sequence, AnimationTrack.ORIENTATION, new AnimationController());

        group.animate(500);
        float[] angleAxis = new float[4];
        group.getOrientation(angleAxis);
        check("squad axis x", approx(angleAxis[1], 0.0f));
        check("squad axis y", approx(angleAxis[2], 0.0f));
        check("squad axis z", angleAxis[3] > 0.99f);
        check("squad angle lower", angleAxis[0] > 75.0f);
        check("squad angle upper", angleAxis[0] < 100.0f);
    }

    private static void testControllerActivation() {
        Group group = new Group();
        KeyframeSequence sequence = new KeyframeSequence(2, 3, KeyframeSequence.LINEAR);
        sequence.setDuration(1000);
        sequence.setKeyframe(0, 0, new float[]{0.0f, 0.0f, 0.0f});
        sequence.setKeyframe(1, 1000, new float[]{100.0f, 0.0f, 0.0f});

        AnimationController controller = new AnimationController();
        controller.setActiveInterval(100, 300);
        addTrack(group, sequence, AnimationTrack.TRANSLATION, controller);

        int validity = group.animate(50);
        float[] translation = new float[3];
        group.getTranslation(translation);
        check("inactive translation unchanged", approx(translation[0], 0.0f));
        check("time to activation", validity == 50);

        validity = group.animate(200);
        group.getTranslation(translation);
        check("active translation", approx(translation[0], 20.0f));
        check("active linear validity", validity == 1);

        controller.setActiveInterval(0, 0);
        validity = group.animate(500);
        group.getTranslation(translation);
        check("permanent active interval translation", approx(translation[0], 50.0f));
        check("permanent active interval validity", validity == 1);
    }

    private static void testWeightedTrackAccumulation() {
        Group group = new Group();

        AnimationController firstController = new AnimationController();
        firstController.setWeight(0.25f);
        addTrack(group, constantTranslation(10.0f), AnimationTrack.TRANSLATION, firstController);

        AnimationController secondController = new AnimationController();
        secondController.setWeight(0.5f);
        addTrack(group, constantTranslation(20.0f), AnimationTrack.TRANSLATION, secondController);

        group.animate(0);
        float[] translation = new float[3];
        group.getTranslation(translation);
        check("weighted sum is not normalized", approx(translation[0], 12.5f));
    }

    private static void testRepeatModesAfterDuration() {
        Group constantGroup = new Group();
        KeyframeSequence constantSequence = new KeyframeSequence(2, 1, KeyframeSequence.STEP);
        constantSequence.setDuration(100);
        constantSequence.setKeyframe(0, 0, new float[]{0.0f});
        constantSequence.setKeyframe(1, 100, new float[]{1.0f});
        constantSequence.setRepeatMode(KeyframeSequence.CONSTANT);
        addTrack(constantGroup, constantSequence, AnimationTrack.VISIBILITY, new AnimationController());
        constantGroup.animate(101);
        check("constant repeat keeps final visibility", constantGroup.isRenderingEnabled());

        Group loopGroup = new Group();
        KeyframeSequence loopSequence = new KeyframeSequence(2, 1, KeyframeSequence.STEP);
        loopSequence.setDuration(100);
        loopSequence.setKeyframe(0, 0, new float[]{0.0f});
        loopSequence.setKeyframe(1, 100, new float[]{1.0f});
        loopSequence.setRepeatMode(KeyframeSequence.LOOP);
        addTrack(loopGroup, loopSequence, AnimationTrack.VISIBILITY, new AnimationController());
        loopGroup.animate(101);
        check("loop repeat wraps visibility", !loopGroup.isRenderingEnabled());
    }

    private static void testControllerLargeWorldTimePrecision() {
        Group group = new Group();
        KeyframeSequence sequence = new KeyframeSequence(2, 3, KeyframeSequence.LINEAR);
        sequence.setDuration(1000);
        sequence.setRepeatMode(KeyframeSequence.LOOP);
        sequence.setKeyframe(0, 0, new float[]{0.0f, 0.0f, 0.0f});
        sequence.setKeyframe(1, 1000, new float[]{1000.0f, 0.0f, 0.0f});

        AnimationController controller = new AnimationController();
        int epoch = 1700000000;
        controller.setPosition(0.0f, epoch);
        addTrack(group, sequence, AnimationTrack.TRANSLATION, controller);

        group.animate(epoch + 16);
        float[] translation = new float[3];
        group.getTranslation(translation);
        check("large world time preserves 16ms animation delta",
                approx(translation[0], 16.0f));
    }

    private static void addTrack(Group group, KeyframeSequence sequence, int property, AnimationController controller) {
        AnimationTrack track = new AnimationTrack(sequence, property);
        track.setController(controller);
        group.addAnimationTrack(track);
    }

    private static KeyframeSequence constantTranslation(float x) {
        KeyframeSequence sequence = new KeyframeSequence(1, 3, KeyframeSequence.STEP);
        sequence.setDuration(1000);
        sequence.setKeyframe(0, 0, new float[]{x, 0.0f, 0.0f});
        return sequence;
    }

    private static float[] quatZ(float degrees) {
        double half = degrees * Math.PI / 360.0;
        return new float[]{0.0f, 0.0f, (float) Math.sin(half), (float) Math.cos(half)};
    }
}
