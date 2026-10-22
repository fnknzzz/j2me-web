package com.sun.jsjvm.bluetooth;

import java.io.IOException;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.RemoteDevice;

public final class L2CAPConnectionImpl implements L2CAPConnection {
    private final int channelId;
    private final int receiveMtu;
    private final int transmitMtu;
    private final RemoteDevice remoteDevice;
    private byte[] pendingPacket;
    private boolean closed;

    public L2CAPConnectionImpl(int channelId, String address, int receiveMtu, int transmitMtu) {
        this.channelId = channelId;
        this.receiveMtu = receiveMtu;
        this.transmitMtu = transmitMtu;
        this.remoteDevice = new CompatRemoteDevice(address);
    }

    public int getTransmitMTU() throws IOException { ensureOpen(); return transmitMtu; }
    public int getReceiveMTU() throws IOException { ensureOpen(); return receiveMtu; }
    public void send(byte[] data) throws IOException {
        ensureOpen();
        if (data == null) throw new NullPointerException();
        if (data.length > transmitMtu) throw new IllegalArgumentException("Packet exceeds transmit MTU");
        String result = BluetoothBridge.request("send", channelId + "\t" + Wire.bytesToHex(data));
        ensureOk(result);
    }
    public int receive(byte[] buffer) throws IOException {
        ensureOpen();
        if (buffer == null) throw new NullPointerException();
        if (pendingPacket == null) loadPacket();
        int length = Math.min(buffer.length, pendingPacket.length);
        System.arraycopy(pendingPacket, 0, buffer, 0, length);
        pendingPacket = null;
        return length;
    }
    public boolean ready() throws IOException {
        ensureOpen();
        if (pendingPacket != null) return true;
        String response = BluetoothBridge.request("ready", Integer.toString(channelId));
        ensureOk(response);
        String[] fields = Wire.split(response, '\t');
        return fields.length > 1 && "1".equals(fields[1]);
    }
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            ensureOk(BluetoothBridge.request("closeChannel", Integer.toString(channelId)));
        }
    }
    public RemoteDevice getRemoteDevice() { return remoteDevice; }

    private void loadPacket() throws IOException {
        String response = BluetoothBridge.request("receive", Integer.toString(channelId));
        ensureOk(response);
        String[] fields = Wire.split(response, '\t');
        pendingPacket = Wire.hexToBytes(fields.length > 1 ? fields[1] : "");
    }
    private void ensureOpen() throws IOException { if (closed) throw new IOException("Connection closed"); }
    public static void ensureOk(String response) throws IOException {
        if (response == null || !response.startsWith("OK")) {
            String[] fields = Wire.split(response == null ? "" : response, '\t');
            throw new IOException(fields.length > 1 ? Wire.unescape(fields[1]) : "Bluetooth operation failed");
        }
    }
    private static final class CompatRemoteDevice extends RemoteDevice { CompatRemoteDevice(String address) { super(address); } }
}
