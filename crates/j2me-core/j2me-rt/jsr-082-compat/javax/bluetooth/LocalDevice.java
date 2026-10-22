package javax.bluetooth;

import com.sun.jsjvm.bluetooth.BluetoothBridge;
import com.sun.jsjvm.bluetooth.L2CAPNotifierImpl;
import com.sun.jsjvm.bluetooth.Wire;
import javax.microedition.io.Connection;

public class LocalDevice {
    private static LocalDevice instance;
    private final DiscoveryAgent discoveryAgent = new DiscoveryAgent();
    private String address;
    private String name;
    private int discoverable = DiscoveryAgent.NOT_DISCOVERABLE;

    private LocalDevice(String response) throws BluetoothStateException {
        String[] parts = Wire.split(response, '\t');
        if (parts.length < 3 || !"OK".equals(parts[0])) {
            throw new BluetoothStateException(parts.length > 1 ? Wire.unescape(parts[1]) : "Bluetooth unavailable");
        }
        address = parts[1];
        name = Wire.unescape(parts[2]);
    }

    public static synchronized LocalDevice getLocalDevice() throws BluetoothStateException {
        if (instance == null) instance = new LocalDevice(BluetoothBridge.request("capabilities", ""));
        return instance;
    }

    public DiscoveryAgent getDiscoveryAgent() { return discoveryAgent; }
    public String getFriendlyName() { return name; }
    public DeviceClass getDeviceClass() { return new DeviceClass(0); }
    public static String getProperty(String property) {
        if ("bluetooth.l2cap.receiveMTU.max".equals(property)) return "65535";
        if ("bluetooth.connected.devices.max".equals(property)) return "7";
        return null;
    }
    public int getDiscoverable() { return discoverable; }
    public String getBluetoothAddress() { return address; }
    public boolean setDiscoverable(int mode) throws BluetoothStateException { discoverable = mode; return true; }
    public ServiceRecord getRecord(Connection notifier) {
        if (!(notifier instanceof L2CAPNotifierImpl)) throw new IllegalArgumentException("Not a Bluetooth notifier");
        return ((L2CAPNotifierImpl)notifier).getServiceRecord();
    }
    public void updateRecord(ServiceRecord record) throws ServiceRegistrationException {
        if (!(record instanceof com.sun.jsjvm.bluetooth.ServiceRecordImpl)) throw new IllegalArgumentException();
    }
    public static boolean isPowerOn() { return instance != null; }
}
