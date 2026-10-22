package com.com.example;

public class VectorSuite {
  public static void main(String[] args) throws Exception {
    java.util.Vector v = new java.util.Vector();
    v.addElement("a");
    v.addElement("b");
    java.util.Enumeration e = v.elements();
    String s = "";
    while (e.hasMoreElements()) {
      Object x = e.nextElement();
      s += (s.length() == 0 ? "" : ",") + String.valueOf(x);
    }
    System.out.println(s);
    System.out.println("size=" + v.size());
    v.removeElement("a");
    System.out.println("after=" + String.valueOf(v.elementAt(0)));
  }
}

