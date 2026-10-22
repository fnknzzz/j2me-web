package javax.bluetooth;

import java.io.IOException;
import javax.microedition.io.Connection;
import com.sun.jsjvm.bluetooth.L2CAPConnectionImpl;

public class RemoteDevice {
    private final String address;
    private String friendlyName;

    protected RemoteDevice(String address) {
        if (address == null || address.length() != 12) throw new IllegalArgumentException("Malformed address");
        Long.parseLong(address, 16);
        this.address = address.toUpperCase();
    }

    public boolean isTrustedDevice() { return false; }
    public String getFriendlyName(boolean alwaysAsk) throws IOException { return friendlyName; }
    public final String getBluetoothAddress() { return address; }
    public boolean equals(Object value) { return value instanceof RemoteDevice && address.equals(((RemoteDevice)value).address); }
    public int hashCode() { return address.hashCode(); }
    public static RemoteDevice getRemoteDevice(Connection connection) throws IOException {
        if (!(connection instanceof L2CAPConnectionImpl)) throw new IllegalArgumentException();
        return ((L2CAPConnectionImpl)connection).getRemoteDevice();
    }
    public boolean authenticate() throws IOException { return false; }
    public boolean authorize(Connection connection) throws IOException { return false; }
    public boolean encrypt(Connection connection, boolean on) throws IOException { return false; }
    public boolean isAuthenticated() { return false; }
    public boolean isAuthorized(Connection connection) throws IOException { return false; }
    public boolean isEncrypted() { return false; }

    void setFriendlyName(String value) { friendlyName = value; }
}
