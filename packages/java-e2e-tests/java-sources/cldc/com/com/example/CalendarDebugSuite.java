package com.com.example;

public class CalendarDebugSuite {
  public static void main(String[] args) throws Exception {
    java.util.Calendar c = java.util.Calendar.getInstance();
    System.out.println("class=" + c.getClass().getName());
    
    c.setTimeInMillis(1234567890000L);
    System.out.println("after_setTime_ms=" + c.getTimeInMillis());
    
    c.set(12, 32);
    System.out.println("after_set_field=" + c.get(12));
    
    long ms = c.getTimeInMillis();
    System.out.println("after_getTimeInMillis=" + ms);
  }
}
