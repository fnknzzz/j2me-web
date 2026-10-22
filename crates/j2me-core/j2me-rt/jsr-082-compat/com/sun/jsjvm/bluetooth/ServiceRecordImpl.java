package com.sun.jsjvm.bluetooth;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.bluetooth.DataElement;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

public final class ServiceRecordImpl implements ServiceRecord {
    private final Hashtable attributes = new Hashtable();
    private final RemoteDevice host;
    private final String uuid;
    private String endpoint;
    private int psm;
    private int serviceClasses;

    public ServiceRecordImpl(RemoteDevice host, String uuid, String endpoint, int psm) {
        this.host = host;
        this.uuid = uuid;
        this.endpoint = Wire.unescape(endpoint);
        this.psm = psm;
        installProtocolDescriptor();
    }

    public DataElement getAttributeValue(int id) { return (DataElement)attributes.get(new Integer(id)); }
    public RemoteDevice getHostDevice() { return host; }
    public int[] getAttributeIDs() {
        int[] ids = new int[attributes.size()];
        Enumeration keys = attributes.keys();
        int i = 0;
        while (keys.hasMoreElements()) ids[i++] = ((Integer)keys.nextElement()).intValue();
        return ids;
    }
    public boolean populateRecord(int[] ids) throws IOException { return true; }
    public String getConnectionURL(int security, boolean master) {
        if (host == null) return null;
        return "btl2cap://" + host.getBluetoothAddress() + ":" + Integer.toString(psm, 16) +
            ";authenticate=false;encrypt=false;master=false;endpoint=" + Wire.escape(endpoint);
    }
    public void setDeviceServiceClasses(int classes) { serviceClasses = classes; }
    public boolean setAttributeValue(int id, DataElement value) {
        Integer key = new Integer(id);
        if (value == null) return attributes.remove(key) != null;
        attributes.put(key, value);
        return true;
    }
    public String getUuid() { return uuid; }
    public String getEndpoint() { return endpoint; }
    public int getPsm() { return psm; }
    public void setPublished(String endpoint, int psm) { this.endpoint = endpoint; this.psm = psm; installProtocolDescriptor(); }

    public String exportAttributes() {
        StringBuffer result = new StringBuffer();
        Enumeration keys = attributes.keys();
        while (keys.hasMoreElements()) {
            Integer key = (Integer)keys.nextElement();
            DataElement element = (DataElement)attributes.get(key);
            String value = encodeValue(element);
            if (value == null) continue;
            if (result.length() > 0) result.append(';');
            result.append(key.intValue());
            result.append('=');
            result.append(element.getDataType());
            result.append('=');
            result.append(Wire.escape(value));
        }
        return result.toString();
    }

    public void importAttributes(String encoded) {
        if (encoded == null || encoded.length() == 0) return;
        String[] entries = Wire.split(Wire.unescape(encoded), ';');
        for (int i = 0; i < entries.length; i++) {
            int first = entries[i].indexOf('=');
            int second = entries[i].indexOf('=', first + 1);
            if (first <= 0 || second < 0) continue;
            try {
                int id = Integer.parseInt(entries[i].substring(0, first));
                int type = Integer.parseInt(entries[i].substring(first + 1, second));
                DataElement value = decodeValue(type, Wire.unescape(entries[i].substring(second + 1)));
                if (value != null) attributes.put(new Integer(id), value);
            } catch (RuntimeException ignored) {}
        }
    }

    private static String encodeValue(DataElement element) {
        int type = element.getDataType();
        switch (type) {
        case DataElement.NULL:
            return "";
        case DataElement.BOOL:
            return element.getBoolean() ? "1" : "0";
        case DataElement.U_INT_1:
        case DataElement.U_INT_2:
        case DataElement.U_INT_4:
        case DataElement.INT_1:
        case DataElement.INT_2:
        case DataElement.INT_4:
        case DataElement.INT_8:
            return Long.toString(element.getLong());
        case DataElement.STRING:
        case DataElement.URL:
        case DataElement.UUID:
            return element.getValue().toString();
        case DataElement.U_INT_8:
        case DataElement.U_INT_16:
        case DataElement.INT_16:
            return Wire.bytesToHex((byte[])element.getValue());
        default:
            return null;
        }
    }

    private static DataElement decodeValue(int type, String value) {
        switch (type) {
        case DataElement.NULL:
            return new DataElement(DataElement.NULL);
        case DataElement.BOOL:
            return new DataElement("1".equals(value));
        case DataElement.U_INT_1:
        case DataElement.U_INT_2:
        case DataElement.U_INT_4:
        case DataElement.INT_1:
        case DataElement.INT_2:
        case DataElement.INT_4:
        case DataElement.INT_8:
            return new DataElement(type, Long.parseLong(value));
        case DataElement.STRING:
        case DataElement.URL:
            return new DataElement(type, value);
        case DataElement.UUID:
            return new DataElement(type, new UUID(value, false));
        case DataElement.U_INT_8:
        case DataElement.U_INT_16:
        case DataElement.INT_16:
            return new DataElement(type, Wire.hexToBytes(value));
        default:
            return null;
        }
    }

    private void installProtocolDescriptor() {
        DataElement outer = new DataElement(DataElement.DATSEQ);
        DataElement protocol = new DataElement(DataElement.DATSEQ);
        protocol.addElement(new DataElement(DataElement.UUID, new UUID(0x0100)));
        protocol.addElement(new DataElement(DataElement.U_INT_2, psm));
        outer.addElement(protocol);
        attributes.put(new Integer(0x0004), outer);
    }
}
