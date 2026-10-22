package com.sun.jsjvm.bluetooth;

import java.io.IOException;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.L2CAPConnectionNotifier;

public final class L2CAPNotifierImpl implements L2CAPConnectionNotifier {
    private final String uuid;
    private final String serviceName;
    private final int receiveMtu;
    private final int transmitMtu;
    private final ServiceRecordImpl record;
    private int listenerId;
    private boolean closed;

    public L2CAPNotifierImpl(String uuid, String serviceName, int receiveMtu, int transmitMtu) {
        this.uuid = uuid;
        this.serviceName = serviceName;
        this.receiveMtu = receiveMtu;
        this.transmitMtu = transmitMtu;
        this.record = new ServiceRecordImpl(null, uuid, "", 0);
    }

    public L2CAPConnection acceptAndOpen() throws IOException {
        ensureOpen();
        if (listenerId == 0) publish();
        String response = BluetoothBridge.request("accept", Integer.toString(listenerId));
        L2CAPConnectionImpl.ensureOk(response);
        String[] fields = Wire.split(response, '\t');
        return new L2CAPConnectionImpl(Integer.parseInt(fields[1]), fields[2],
            Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (listenerId != 0) L2CAPConnectionImpl.ensureOk(BluetoothBridge.request("closeListener", Integer.toString(listenerId)));
        }
    }

    public ServiceRecordImpl getServiceRecord() { return record; }

    private void publish() throws IOException {
        String payload = uuid + "\t" + Wire.escape(serviceName) + "\t" + receiveMtu + "\t" + transmitMtu +
            "\t" + Wire.escape(record.exportAttributes());
        String response = BluetoothBridge.request("publish", payload);
        L2CAPConnectionImpl.ensureOk(response);
        String[] fields = Wire.split(response, '\t');
        listenerId = Integer.parseInt(fields[1]);
        record.setPublished(Wire.unescape(fields[2]), Integer.parseInt(fields[3]));
    }
    private void ensureOpen() throws IOException { if (closed) throw new IOException("Notifier closed"); }
}
