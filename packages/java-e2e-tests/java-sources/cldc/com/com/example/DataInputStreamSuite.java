package com.com.example;

public class DataInputStreamSuite {
  public static void main(String[] args) {
    try {
      System.out.println("Test 1: Basic type reading");
      byte[] data1 = new byte[]{
        0x01,
        (byte)0x7F,
        (byte)0xFF,
        (byte)0x12, (byte)0x34,
        (byte)0x56, (byte)0x78,
        (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78,
        (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78, (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0,
        (byte)0x40, (byte)0x49, (byte)0x0F, (byte)0xDB,
        (byte)0x40, (byte)0x09, (byte)0x21, (byte)0xFB, (byte)0x54, (byte)0x44, (byte)0x2D, (byte)0x18
      };

      java.io.ByteArrayInputStream bis1 = new java.io.ByteArrayInputStream(data1);
      java.io.DataInputStream dis1 = new java.io.DataInputStream(bis1);

      System.out.println("available: " + dis1.available());

      boolean bool = dis1.readBoolean();
      System.out.println("readBoolean: " + bool);

      byte b = dis1.readByte();
      System.out.println("readByte: " + b);

      int ub = dis1.readUnsignedByte();
      System.out.println("readUnsignedByte: " + ub);

      short s = dis1.readShort();
      System.out.println("readShort: " + s);

      int us = dis1.readUnsignedShort();
      System.out.println("readUnsignedShort: " + us);

      int i = dis1.readInt();
      System.out.println("readInt: " + i);

      long l = dis1.readLong();
      System.out.println("readLong: " + l);

      float f = dis1.readFloat();
      System.out.println("readFloat: " + f);

      double d = dis1.readDouble();
      System.out.println("readDouble: " + d);

      System.out.println("available after: " + dis1.available());
      dis1.close();

      System.out.println("Test 2: UTF string reading");
      byte[] utfData = new byte[]{(byte)0x00, (byte)0x05, (byte)'H', (byte)'e', (byte)'l', (byte)'l', (byte)'o'};
      java.io.ByteArrayInputStream bisUtf = new java.io.ByteArrayInputStream(utfData);
      java.io.DataInputStream disUtf = new java.io.DataInputStream(bisUtf);
      String str = disUtf.readUTF();
      System.out.println("readUTF: " + str);
      disUtf.close();

      System.out.println("\nTest 3: readFully and skipBytes");
      byte[] data2 = new byte[]{10, 20, 30, 40, 50};
      java.io.ByteArrayInputStream bis2 = new java.io.ByteArrayInputStream(data2);
      java.io.DataInputStream dis2 = new java.io.DataInputStream(bis2);

      byte[] buf = new byte[3];
      dis2.readFully(buf);
      System.out.println("readFully: " + buf[0] + "," + buf[1] + "," + buf[2]);

      byte[] buf2 = new byte[5];
      dis2.readFully(buf2, 0, 2);
      System.out.println("readFully(off,len): " + buf2[0] + "," + buf2[1]);

      System.out.println("skipBytes: " + dis2.skipBytes(10));
      dis2.close();

      System.out.println("\nTest 4: UTF with ASCII");
      byte[] data3 = new byte[]{(byte)0x00, (byte)0x03, (byte)'A', (byte)'B', (byte)'C'};
      java.io.ByteArrayInputStream bis3 = new java.io.ByteArrayInputStream(data3);
      java.io.DataInputStream dis3 = new java.io.DataInputStream(bis3);
      String str2 = dis3.readUTF();
      System.out.println("readUTF: " + str2);
      dis3.close();

      System.out.println("\nTest 5: read methods");

      byte[] data4 = new byte[]{65, 66, 67};
      java.io.ByteArrayInputStream bis4 = new java.io.ByteArrayInputStream(data4);
      java.io.DataInputStream dis4 = new java.io.DataInputStream(bis4);
      System.out.println("read(): " + dis4.read());
      byte[] buf3 = new byte[2];
      System.out.println("read(buf): " + dis4.read(buf3));
      System.out.println("buf3: " + buf3[0] + "," + buf3[1]);
      dis4.close();

      System.out.println("\nTest 6: read with offset and length");
      byte[] data5 = new byte[]{10, 20, 30, 40, 50};
      java.io.ByteArrayInputStream bis5 = new java.io.ByteArrayInputStream(data5);
      java.io.DataInputStream dis5 = new java.io.DataInputStream(bis5);
      byte[] buf4 = new byte[5];
      System.out.println("read(buf,off,len): " + dis5.read(buf4, 1, 3));
      System.out.println("buf4: " + buf4[0] + "," + buf4[1] + "," + buf4[2] + "," + buf4[3]);
      dis5.close();

      System.out.println("\nTest 7: skip method");
      byte[] data6 = new byte[]{10, 20, 30};
      java.io.ByteArrayInputStream bis6 = new java.io.ByteArrayInputStream(data6);
      java.io.DataInputStream dis6 = new java.io.DataInputStream(bis6);
      System.out.println("skip: " + dis6.skip(2));
      System.out.println("read after skip: " + dis6.read());
      dis6.close();

      System.out.println("\nTest 8: mark and reset");
      byte[] data7 = new byte[]{10, 20, 30};
      java.io.ByteArrayInputStream bis7 = new java.io.ByteArrayInputStream(data7);
      java.io.DataInputStream dis7 = new java.io.DataInputStream(bis7);
      System.out.println("markSupported: " + dis7.markSupported());
      dis7.mark(10);
      System.out.println("read1: " + dis7.read());
      dis7.reset();
      System.out.println("read2: " + dis7.read());
      dis7.close();

      System.out.println("\nTest 9: null InputStream throws NullPointerException on read");
      try {
        java.io.DataInputStream dis8 = new java.io.DataInputStream(null);
        System.out.println("Created DataInputStream with null");
        dis8.read();
        System.out.println("ERROR: should have thrown NullPointerException");
      } catch (NullPointerException e) {
        System.out.println("NullPointerException caught as expected");
      }
    } catch (Exception e) {
      System.out.println("Exception: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
