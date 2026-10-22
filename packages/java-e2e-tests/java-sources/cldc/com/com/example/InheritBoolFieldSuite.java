package com.com.example;

class ParentBool {
  private boolean parentFlag = true;
  
  public void setParentFlagFalse() {
    this.parentFlag = false;
  }
  
  public boolean getParentFlag() {
    return this.parentFlag;
  }
  
  public boolean checkParentFlag() {
    if (!this.parentFlag) {
      return true;
    }
    return false;
  }
}

class ChildBool extends ParentBool {
  private boolean childFlag = false;
  
  public void setChildFlagTrue() {
    this.childFlag = true;
  }
  
  public boolean getChildFlag() {
    return this.childFlag;
  }
}

public class InheritBoolFieldSuite {
  public static void main(String[] args) {
    ChildBool child = new ChildBool();
    System.out.println("parent_initial=" + child.getParentFlag());
    System.out.println("child_initial=" + child.getChildFlag());
    
    child.setParentFlagFalse();
    System.out.println("parent_after_set=" + child.getParentFlag());
    System.out.println("check_parent=" + child.checkParentFlag());
    
    child.setChildFlagTrue();
    System.out.println("child_after_set=" + child.getChildFlag());
  }
}
