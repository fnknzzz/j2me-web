package javax.bluetooth;

import com.sun.jsjvm.bluetooth.BluetoothBridge;
import com.sun.jsjvm.bluetooth.ServiceRecordImpl;
import com.sun.jsjvm.bluetooth.Wire;
import java.util.Vector;

public class DiscoveryAgent {
    public static final int NOT_DISCOVERABLE = 0;
    public static final int GIAC = 0x9E8B33;
    public static final int LIAC = 0x9E8B00;
    public static final int CACHED = 0;
    public static final int PREKNOWN = 1;

    private final Vector cachedDevices = new Vector();
    private int nextTransactionId = 1;

    DiscoveryAgent() {}

    public RemoteDevice[] retrieveDevices(int option) {
        if (cachedDevices.size() == 0) return null;
        RemoteDevice[] result = new RemoteDevice[cachedDevices.size()];
        cachedDevices.copyInto(result);
        return result;
    }

    public boolean startInquiry(int accessCode, DiscoveryListener listener) throws BluetoothStateException {
        if (listener == null) throw new NullPointerException();
        if (accessCode != GIAC && accessCode != LIAC) throw new IllegalArgumentException();
        String response = BluetoothBridge.request("discover", "");
        if (response == null || response.startsWith("ERR\t")) throw new BluetoothStateException(error(response));
        String[] lines = Wire.split(response, '\n');
        for (int i = 1; i < lines.length; i++) {
            String[] fields = Wire.split(lines[i], '\t');
            if (fields.length < 3) continue;
            RemoteDevice device = findOrCreate(fields[0], Wire.unescape(fields[1]));
            listener.deviceDiscovered(device, new DeviceClass(parseInt(fields[2], 0)));
        }
        listener.inquiryCompleted(DiscoveryListener.INQUIRY_COMPLETED);
        return true;
    }

    public boolean cancelInquiry(DiscoveryListener listener) { return false; }

    public int searchServices(int[] attrSet, UUID[] uuidSet, RemoteDevice device, DiscoveryListener listener) throws BluetoothStateException {
        if (uuidSet == null || uuidSet.length == 0 || device == null || listener == null) throw new NullPointerException();
        int transactionId = nextTransactionId++;
        StringBuffer uuids = new StringBuffer();
        for (int i = 0; i < uuidSet.length; i++) {
            if (i > 0) uuids.append(',');
            uuids.append(uuidSet[i].toString());
        }
        String response = BluetoothBridge.request("discover", device.getBluetoothAddress() + "\t" + uuids);
        if (response == null || response.startsWith("ERR\t")) {
            listener.serviceSearchCompleted(transactionId, DiscoveryListener.SERVICE_SEARCH_ERROR);
            return transactionId;
        }
        String[] lines = Wire.split(response, '\n');
        Vector records = new Vector();
        for (int i = 1; i < lines.length; i++) {
            String[] fields = Wire.split(lines[i], '\t');
            if (fields.length < 6 || !device.getBluetoothAddress().equals(fields[0])) continue;
            ServiceRecordImpl record = new ServiceRecordImpl(device, fields[3], fields[4], parseInt(fields[5], 0));
            if (fields.length > 6) record.importAttributes(fields[6]);
            records.addElement(record);
        }
        if (records.size() > 0) {
            ServiceRecord[] result = new ServiceRecord[records.size()];
            records.copyInto(result);
            listener.servicesDiscovered(transactionId, result);
            listener.serviceSearchCompleted(transactionId, DiscoveryListener.SERVICE_SEARCH_COMPLETED);
        } else {
            listener.serviceSearchCompleted(transactionId, DiscoveryListener.SERVICE_SEARCH_NO_RECORDS);
        }
        return transactionId;
    }

    public boolean cancelServiceSearch(int transactionId) { return false; }
    public String selectService(UUID uuid, int security, boolean master) throws BluetoothStateException { return null; }

    private RemoteDevice findOrCreate(String address, String name) {
        for (int i = 0; i < cachedDevices.size(); i++) {
            RemoteDevice device = (RemoteDevice)cachedDevices.elementAt(i);
            if (device.getBluetoothAddress().equals(address)) return device;
        }
        RemoteDevice device = new CompatRemoteDevice(address, name);
        cachedDevices.addElement(device);
        return device;
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }
    private static String error(String response) {
        if (response == null) return "Bluetooth unavailable";
        String[] fields = Wire.split(response, '\t');
        return fields.length > 1 ? Wire.unescape(fields[1]) : "Bluetooth error";
    }

    private static final class CompatRemoteDevice extends RemoteDevice {
        CompatRemoteDevice(String address, String name) { super(address); setFriendlyName(name); }
    }
}
