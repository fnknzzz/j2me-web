package com.com.example;

public class InputStreamSuite {
  public static void main(String[] args) {
    try {
      System.out.println("Test 1: InputStream basic methods");
      java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(new byte[]{10, 20, 30});
      System.out.println("Available: " + bis.available());
      System.out.println("Mark supported: " + bis.markSupported());
      
      System.out.println("\nTest 2: InputStream read operations");
      System.out.println("Read byte: " + bis.read());
      System.out.println("Available after read: " + bis.available());
      
      byte[] buffer = new byte[2];
      int bytesRead = bis.read(buffer);
      System.out.println("Bytes read into buffer: " + bytesRead);
      System.out.println("Buffer[0]: " + buffer[0]);
      System.out.println("Buffer[1]: " + buffer[1]);
      
      System.out.println("\nTest 3: InputStream mark and reset");
      java.io.ByteArrayInputStream bis2 = new java.io.ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5});
      bis2.mark(10);
      System.out.println("Read after mark: " + bis2.read());
      System.out.println("Read after mark: " + bis2.read());
      bis2.reset();
      System.out.println("Read after reset: " + bis2.read());
      
      System.out.println("\nTest 4: InputStream skip");
      java.io.ByteArrayInputStream bis3 = new java.io.ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5});
      long skipped = bis3.skip(2);
      System.out.println("Bytes skipped: " + skipped);
      System.out.println("Read after skip: " + bis3.read());
      
      System.out.println("\nTest 5: InputStream read with offset and length");
      java.io.ByteArrayInputStream bis4 = new java.io.ByteArrayInputStream(new byte[]{10, 20, 30, 40, 50});
      byte[] buffer2 = new byte[5];
      int bytesRead2 = bis4.read(buffer2, 1, 3);
      System.out.println("Bytes read: " + bytesRead2);
      System.out.println("Buffer[0]: " + buffer2[0]);
      System.out.println("Buffer[1]: " + buffer2[1]);
      System.out.println("Buffer[2]: " + buffer2[2]);
      System.out.println("Buffer[3]: " + buffer2[3]);
      System.out.println("Buffer[4]: " + buffer2[4]);
      
      System.out.println("\nTest 6: InputStream close");
      bis.close();
      bis2.close();
      bis3.close();
      bis4.close();
      System.out.println("All streams closed");

      System.out.println("\nAll tests completed successfully!");

    } catch (Exception e) {
      System.out.println("Exception: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
