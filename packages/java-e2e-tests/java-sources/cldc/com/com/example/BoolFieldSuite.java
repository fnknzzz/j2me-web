package com.com.example;

public class BoolFieldSuite {
  private boolean flag = true;
  
  public void setFlagFalse() {
    this.flag = false;
  }
  
  public boolean getFlag() {
    return this.flag;
  }
  
  public static void main(String[] args) {
    BoolFieldSuite obj = new BoolFieldSuite();
    System.out.println("initial=" + obj.getFlag());
    obj.setFlagFalse();
    System.out.println("after_set=" + obj.getFlag());
    
    if (!obj.getFlag()) {
      System.out.println("condition_passed");
    } else {
      System.out.println("condition_failed");
    }
  }
}
