package com.sun.midp.rms;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.microedition.rms.InvalidRecordIDException;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotFoundException;
import javax.microedition.rms.RecordStoreNotOpenException;

import com.sun.midp.security.SecurityToken;

public class RecordStoreImpl implements AbstractRecordStoreImpl {

    private String storeName;
    private Hashtable records; // Integer -> byte[]
    private int nextId;
    private int version;
    private long lastModified;
    private int authMode;

    // ---- native persistence methods ----
    private static native byte[] nativeLoad(String key);
    private static native void nativeSave(String key, byte[] data);
    private static native void nativeRemove(String key);
    private static native String[] nativeKeys();

    // ---- LE byte helpers (no ByteBuffer in J2ME) ----

    private static void writeI32LE(byte[] buf, int offset, int value) {
        buf[offset]     = (byte)(value & 0xFF);
        buf[offset + 1] = (byte)((value >> 8) & 0xFF);
        buf[offset + 2] = (byte)((value >> 16) & 0xFF);
        buf[offset + 3] = (byte)((value >> 24) & 0xFF);
    }

    private static int readI32LE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
             | ((buf[offset + 1] & 0xFF) << 8)
             | ((buf[offset + 2] & 0xFF) << 16)
             | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static void writeF64LE(byte[] buf, int offset, double value) {
        long bits = Double.doubleToLongBits(value);
        buf[offset]     = (byte)(bits & 0xFF);
        buf[offset + 1] = (byte)((bits >> 8) & 0xFF);
        buf[offset + 2] = (byte)((bits >> 16) & 0xFF);
        buf[offset + 3] = (byte)((bits >> 24) & 0xFF);
        buf[offset + 4] = (byte)((bits >> 32) & 0xFF);
        buf[offset + 5] = (byte)((bits >> 40) & 0xFF);
        buf[offset + 6] = (byte)((bits >> 48) & 0xFF);
        buf[offset + 7] = (byte)((bits >> 56) & 0xFF);
    }

    private static double readF64LE(byte[] buf, int offset) {
        long bits = (buf[offset] & 0xFFL)
                  | ((buf[offset + 1] & 0xFFL) << 8)
                  | ((buf[offset + 2] & 0xFFL) << 16)
                  | ((buf[offset + 3] & 0xFFL) << 24)
                  | ((buf[offset + 4] & 0xFFL) << 32)
                  | ((buf[offset + 5] & 0xFFL) << 40)
                  | ((buf[offset + 6] & 0xFFL) << 48)
                  | ((buf[offset + 7] & 0xFFL) << 56);
        return Double.longBitsToDouble(bits);
    }

    // ---- serialization (matches TS serializeStore) ----

    private byte[] serialize() {
        int totalSize = 4 + 4 + 8 + 4; // header: nextId + version + lastModified + recordCount
        Enumeration keys = records.keys();
        while (keys.hasMoreElements()) {
            Integer id = (Integer) keys.nextElement();
            byte[] data = (byte[]) records.get(id);
            totalSize += 4 + 4 + data.length; // id + dataLen + data
        }

        byte[] buf = new byte[totalSize];
        int off = 0;

        writeI32LE(buf, off, nextId);    off += 4;
        writeI32LE(buf, off, version);   off += 4;
        writeF64LE(buf, off, (double) lastModified); off += 8;
        writeI32LE(buf, off, records.size()); off += 4;

        Enumeration keys2 = records.keys();
        while (keys2.hasMoreElements()) {
            Integer id = (Integer) keys2.nextElement();
            byte[] data = (byte[]) records.get(id);
            writeI32LE(buf, off, id.intValue()); off += 4;
            writeI32LE(buf, off, data.length);   off += 4;
            System.arraycopy(data, 0, buf, off, data.length);
            off += data.length;
        }

        return buf;
    }

    private void deserialize(byte[] buf) {
        int off = 0;
        nextId       = readI32LE(buf, off); off += 4;
        version      = readI32LE(buf, off); off += 4;
        lastModified = (long) readF64LE(buf, off); off += 8;
        int count    = readI32LE(buf, off); off += 4;

        records = new Hashtable();
        for (int i = 0; i < count; i++) {
            int id     = readI32LE(buf, off); off += 4;
            int len    = readI32LE(buf, off); off += 4;
            byte[] data = new byte[len];
            System.arraycopy(buf, off, data, 0, len);
            off += len;
            records.put(new Integer(id), data);
        }
    }

    private void persist() {
        nativeSave("rms:" + storeName, serialize());
    }

    // ---- constructor ----

    private RecordStoreImpl(String name, boolean create)
        throws RecordStoreException, RecordStoreNotFoundException {

        this.storeName = name;
        this.authMode = 0;

        byte[] raw = nativeLoad("rms:" + name);
        if (raw != null) {
            deserialize(raw);
        } else if (create) {
            records = new Hashtable();
            nextId = 1;
            version = 0;
            lastModified = System.currentTimeMillis();
            persist();
        } else {
            throw new RecordStoreNotFoundException("cannot find record store file");
        }
    }

    // ---- static API used by RecordStore.java ----

    public static RecordStoreImpl openRecordStore(SecurityToken token,
                                                  int suiteId,
                                                  String recordStoreName,
                                                  boolean createIfNecessary)
        throws RecordStoreException, RecordStoreFullException,
               RecordStoreNotFoundException {
        return new RecordStoreImpl(recordStoreName, createIfNecessary);
    }

    public static void deleteRecordStore(SecurityToken token,
                                         int suiteId,
                                         String recordStoreName)
        throws RecordStoreException, RecordStoreNotFoundException {

        nativeRemove("rms:" + recordStoreName);
    }

    public static String[] listRecordStores(SecurityToken token,
                                            int suiteId) {
        String[] allKeys = nativeKeys();
        if (allKeys == null || allKeys.length == 0) {
            return null;
        }

        // count matching keys
        int count = 0;
        for (int i = 0; i < allKeys.length; i++) {
            if (allKeys[i].startsWith("rms:")) {
                count++;
            }
        }
        if (count == 0) {
            return null;
        }

        String[] result = new String[count];
        int idx = 0;
        for (int i = 0; i < allKeys.length; i++) {
            if (allKeys[i].startsWith("rms:")) {
                result[idx++] = allKeys[i].substring(4);
            }
        }
        return result;
    }

    // ---- AbstractRecordStoreImpl interface ----

    public void setMode(int authmode, boolean writable)
        throws RecordStoreException {
        this.authMode = authmode;
    }

    public void closeRecordStore()
        throws RecordStoreNotOpenException, RecordStoreException {
        persist();
    }

    public int getAuthMode() {
        return authMode;
    }

    public int getVersion() throws RecordStoreNotOpenException {
        return version;
    }

    public int getNumRecords() {
        return records.size();
    }

    public int getSize() {
        int total = 0;
        Enumeration vals = records.elements();
        while (vals.hasMoreElements()) {
            byte[] data = (byte[]) vals.nextElement();
            total += data.length;
        }
        return total;
    }

    public int getSizeAvailable() {
        return 1024 * 1024;
    }

    public long getLastModified() {
        return lastModified;
    }

    public int getNextRecordID() {
        return nextId;
    }

    public int addRecord(byte[] data, int offset, int numBytes)
        throws RecordStoreNotOpenException, RecordStoreException,
               RecordStoreFullException {

        int recordId = nextId++;
        byte[] copy = new byte[numBytes];
        if (data != null && numBytes > 0) {
            System.arraycopy(data, offset, copy, 0, numBytes);
        }
        records.put(new Integer(recordId), copy);
        version++;
        lastModified = System.currentTimeMillis();
        persist();
        return recordId;
    }

    public void deleteRecord(int recordId)
        throws RecordStoreNotOpenException, InvalidRecordIDException,
               RecordStoreException {

        Integer key = new Integer(recordId);
        if (records.get(key) == null) {
            throw new InvalidRecordIDException("invalid record id: " + recordId);
        }
        records.remove(key);
        version++;
        lastModified = System.currentTimeMillis();
        persist();
    }

    public int getRecordSize(int recordId)
        throws RecordStoreNotOpenException, InvalidRecordIDException,
               RecordStoreException {

        Integer key = new Integer(recordId);
        byte[] data = (byte[]) records.get(key);
        if (data == null) {
            throw new InvalidRecordIDException("invalid record id: " + recordId);
        }
        return data.length;
    }

    public int getRecord(int recordId, byte[] buffer, int offset)
        throws RecordStoreNotOpenException, InvalidRecordIDException,
               RecordStoreException {

        Integer key = new Integer(recordId);
        byte[] data = (byte[]) records.get(key);
        if (data == null) {
            throw new InvalidRecordIDException("invalid record id: " + recordId);
        }
        System.arraycopy(data, 0, buffer, offset, data.length);
        return data.length;
    }

    public byte[] getRecord(int recordId)
        throws RecordStoreNotOpenException, InvalidRecordIDException,
               RecordStoreException {

        Integer key = new Integer(recordId);
        byte[] data = (byte[]) records.get(key);
        if (data == null) {
            throw new InvalidRecordIDException("invalid record id: " + recordId);
        }
        if (data.length == 0) {
            return null;
        }
        byte[] copy = new byte[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        return copy;
    }

    public void setRecord(int recordId, byte[] newData,
                          int offset, int numBytes)
        throws RecordStoreNotOpenException, InvalidRecordIDException,
               RecordStoreException, RecordStoreFullException {

        Integer key = new Integer(recordId);
        byte[] copy = new byte[numBytes];
        if (newData != null && numBytes > 0) {
            System.arraycopy(newData, offset, copy, 0, numBytes);
        }
        records.put(key, copy);
        // Maintain nextId so subsequent addRecord won't collide
        if (recordId >= nextId) {
            nextId = recordId + 1;
        }
        version++;
        lastModified = System.currentTimeMillis();
        persist();
    }

    public int[] getRecordIDs() {
        int[] ids = new int[records.size()];
        Enumeration keys = records.keys();
        int i = 0;
        while (keys.hasMoreElements()) {
            ids[i++] = ((Integer) keys.nextElement()).intValue();
        }
        return ids;
    }

    public AbstractRecordStoreFile getDbFile() {
        return null;
    }

    public AbstractRecordStoreFile createIndexFile(int suiteId, String name)
        throws IOException {
        return null;
    }
}
