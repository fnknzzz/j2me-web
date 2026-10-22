package com.sun.cldc.io.j2me.btl2cap;

import com.sun.j2me.io.ConnectionBaseInterface;
import com.sun.jsjvm.bluetooth.BluetoothBridge;
import com.sun.jsjvm.bluetooth.L2CAPConnectionImpl;
import com.sun.jsjvm.bluetooth.L2CAPNotifierImpl;
import com.sun.jsjvm.bluetooth.Wire;
import java.io.IOException;
import javax.bluetooth.L2CAPConnection;
import javax.microedition.io.Connection;

public final class Protocol implements ConnectionBaseInterface {
    public Connection openPrim(String name, int mode, boolean timeouts) throws IOException {
        if (name == null || !name.startsWith("//")) throw new IllegalArgumentException("Malformed btl2cap URL");
        String value = name.substring(2);
        int separator = value.indexOf(':');
        if (separator < 1) throw new IllegalArgumentException("Missing service");
        String target = value.substring(0, separator);
        String rest = value.substring(separator + 1);
        int optionsAt = rest.indexOf(';');
        String service = optionsAt < 0 ? rest : rest.substring(0, optionsAt);
        String options = optionsAt < 0 ? "" : rest.substring(optionsAt + 1);
        int receiveMtu = intOption(options, "ReceiveMTU", L2CAPConnection.DEFAULT_MTU);
        int transmitMtu = intOption(options, "TransmitMTU", L2CAPConnection.DEFAULT_MTU);
        if (receiveMtu < L2CAPConnection.MINIMUM_MTU || transmitMtu < L2CAPConnection.MINIMUM_MTU) throw new IllegalArgumentException("MTU too small");

        if ("localhost".equals(target)) {
            return new L2CAPNotifierImpl(service, stringOption(options, "name", ""), receiveMtu, transmitMtu);
        }
        if (target.length() != 12) throw new IllegalArgumentException("Malformed Bluetooth address");
        String endpoint = stringOption(options, "endpoint", "");
        String payload = target + "\t" + service + "\t" + Wire.escape(endpoint) + "\t" + receiveMtu + "\t" + transmitMtu;
        String response = BluetoothBridge.request("connect", payload);
        L2CAPConnectionImpl.ensureOk(response);
        String[] fields = Wire.split(response, '\t');
        return new L2CAPConnectionImpl(Integer.parseInt(fields[1]), fields[2], Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
    }

    private static String stringOption(String options, String name, String fallback) {
        String prefix = name + "=";
        String[] parts = Wire.split(options, ';');
        for (int i = 0; i < parts.length; i++) if (parts[i].startsWith(prefix)) return Wire.unescape(parts[i].substring(prefix.length()));
        return fallback;
    }
    private static int intOption(String options, String name, int fallback) {
        try { return Integer.parseInt(stringOption(options, name, Integer.toString(fallback))); } catch (Exception e) { return fallback; }
    }
}
