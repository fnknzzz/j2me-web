package com.com.example;

public class HashtableSuite {
  public static void main(String[] args) throws Exception {
    java.util.Hashtable ht = new java.util.Hashtable();
    System.out.println("empty=" + String.valueOf(ht.isEmpty()));
    Object prev = ht.put("a", "1");
    System.out.println("prev=" + String.valueOf(prev));
    ht.put("b", "2");
    System.out.println("size=" + ht.size());
    System.out.println("get-a=" + String.valueOf(ht.get("a")));
    java.util.Enumeration ek = ht.keys();
    java.util.Enumeration ev = ht.elements();
    java.util.Vector kv = new java.util.Vector();
    java.util.Vector vv = new java.util.Vector();
    while (ek.hasMoreElements()) {
      kv.addElement(ek.nextElement());
    }
    while (ev.hasMoreElements()) {
      vv.addElement(ev.nextElement());
    }
    sortVector(kv);
    sortVector(vv);
    String ks = "";
    String vs = "";
    for (int i = 0; i < kv.size(); i++) {
      ks += (i == 0 ? "" : ",") + String.valueOf(kv.elementAt(i));
    }
    for (int i = 0; i < vv.size(); i++) {
      vs += (i == 0 ? "" : ",") + String.valueOf(vv.elementAt(i));
    }
    System.out.println("keys=" + ks);
    System.out.println("vals=" + vs);
    Object removed = ht.remove("a");
    System.out.println("removed=" + String.valueOf(removed));
    System.out.println("containsKey-a=" + String.valueOf(ht.containsKey("a")));
    System.out.println("contains-2=" + String.valueOf(ht.contains("2")));
    ht.clear();
    System.out.println("finalSize=" + ht.size());
  }

  static void sortVector(java.util.Vector v) {
    for (int i = 0; i < v.size() - 1; i++) {
      for (int j = i + 1; j < v.size(); j++) {
        String a = String.valueOf(v.elementAt(i));
        String b = String.valueOf(v.elementAt(j));
        if (a.compareTo(b) > 0) {
          Object tmp = v.elementAt(i);
          v.setElementAt(v.elementAt(j), i);
          v.setElementAt(tmp, j);
        }
      }
    }
  }
}

