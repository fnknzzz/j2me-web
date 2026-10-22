package com.com.example;

public class CalendarSuite {
  public static void main(String[] args) throws Exception {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.setTimeInMillis(1234567890000L);
    System.out.println("ms=" + c.getTimeInMillis());
    java.util.Date d = c.getTime();
    System.out.println("ds=" + d.toString());

    java.util.Calendar c2 = java.util.Calendar.getInstance();
    c2.setTimeInMillis(1234567890000L);
    java.util.TimeZone tz = c.getTimeZone();
    c2.setTimeZone(tz);
    System.out.println("eq=" + String.valueOf(c.equals(c2)));
    System.out.println("before=" + String.valueOf(c.before(c2)));
    System.out.println("after=" + String.valueOf(c.after(c2)));

    c.set(12, 32); // MINUTE = 12
    System.out.println("min=" + c.get(12));
    System.out.println("ms2=" + c.getTimeInMillis());
  }
}
