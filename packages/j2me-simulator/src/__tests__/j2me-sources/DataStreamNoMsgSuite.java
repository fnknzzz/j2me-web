import javax.microedition.midlet.*;
import java.io.*;

public class DataStreamNoMsgSuite extends MIDlet {
    public DataStreamNoMsgSuite() {
    }

    protected void startApp() {
        System.out.println("=== DataInputStream No Message Test ===");
        
        try {
            InputStream is = getClass().getResourceAsStream("/config.bin");
            if (is == null) {
                System.out.println("Resource not found");
                return;
            }
            
            System.out.println("Resource found");
            DataInputStream dis = new DataInputStream(is);
            
            int value = dis.readInt();
            System.out.print("Read int value: ");
            System.out.println(value);
            
            dis.close();
            System.out.println("DataInputStream test passed");
        } catch (IOException e) {
            System.out.println("IOException caught");
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
