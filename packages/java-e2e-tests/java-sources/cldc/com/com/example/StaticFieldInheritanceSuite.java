package com.com.example;

public class StaticFieldInheritanceSuite {
    public static void main(String[] args) {
        System.out.println("=== Static Field Inheritance Test ===");
        
        System.out.println("Test 1: Child class static field with same name as parent instance field");
        System.out.println("Child.value (static) = " + Child.value);
        Child.value = 200;
        System.out.println("After Child.value = 200: " + Child.value);
        
        System.out.println("Test 2: Parent instance field is independent");
        Child child = new Child();
        System.out.println("child.getParentValue() (instance) = " + child.getParentValue());
        child.setParentValue(300);
        System.out.println("After setParentValue(300): " + child.getParentValue());
        
        System.out.println("Test 3: Static and instance fields are separate");
        System.out.println("Child.value (static) still = " + Child.value);
        System.out.println("child.getParentValue() (instance) still = " + child.getParentValue());
        
        System.out.println("Test 4: Multiple levels of inheritance");
        System.out.println("GrandChild.value (static) = " + GrandChild.value);
        GrandChild gc = new GrandChild();
        System.out.println("gc.getParentValue() (instance) = " + gc.getParentValue());
        
        System.out.println("Test 5: putstatic on GrandChild (inherited static field)");
        GrandChild.value = 500;
        System.out.println("After GrandChild.value = 500: " + GrandChild.value);
        System.out.println("Child.value should also be 500: " + Child.value);
        
        System.out.println("Test 6: getstatic/putstatic with different field types");
        System.out.println("ChildWithMultipleFields.count (static) = " + ChildWithMultipleFields.count);
        System.out.println("ChildWithMultipleFields.name (static) = " + ChildWithMultipleFields.name);
        ChildWithMultipleFields.count = 999;
        ChildWithMultipleFields.name = "updated";
        System.out.println("After update - count: " + ChildWithMultipleFields.count + ", name: " + ChildWithMultipleFields.name);
        
        System.out.println("Test 7: GrandChildWithMultipleFields inherits static fields");
        System.out.println("GrandChildWithMultipleFields.count (inherited static) = " + GrandChildWithMultipleFields.count);
        System.out.println("GrandChildWithMultipleFields.name (inherited static) = " + GrandChildWithMultipleFields.name);
        GrandChildWithMultipleFields.count = 1234;
        GrandChildWithMultipleFields.name = "grandchild";
        System.out.println("After update via GrandChild - count: " + GrandChildWithMultipleFields.count + ", name: " + GrandChildWithMultipleFields.name);
        System.out.println("ChildWithMultipleFields should reflect same values - count: " + ChildWithMultipleFields.count + ", name: " + ChildWithMultipleFields.name);
        
        System.out.println("Test 8: Instance fields remain independent after static field operations");
        GrandChildWithMultipleFields gcm = new GrandChildWithMultipleFields();
        System.out.println("gcm.getParentCount() (instance) = " + gcm.getParentCount());
        System.out.println("gcm.getParentName() (instance) = " + gcm.getParentName());
        gcm.setParentCount(777);
        gcm.setParentName("instance-name");
        System.out.println("After instance update - count: " + gcm.getParentCount() + ", name: " + gcm.getParentName());
        System.out.println("Static fields unchanged - count: " + GrandChildWithMultipleFields.count + ", name: " + GrandChildWithMultipleFields.name);
        
        System.out.println("=== All Tests Passed ===");
    }
}

class Parent {
    public int value = 10;
    
    public int getParentValue() {
        return this.value;
    }
    
    public void setParentValue(int v) {
        this.value = v;
    }
}

class Child extends Parent {
    public static int value = 100;
}

class GrandChild extends Child {
}

class ParentWithMultipleFields {
    public int count = 1;
    public String name = "parent";
    
    public int getParentCount() {
        return this.count;
    }
    
    public String getParentName() {
        return this.name;
    }
    
    public void setParentCount(int c) {
        this.count = c;
    }
    
    public void setParentName(String n) {
        this.name = n;
    }
}

class ChildWithMultipleFields extends ParentWithMultipleFields {
    public static int count = 100;
    public static String name = "child-static";
}

class GrandChildWithMultipleFields extends ChildWithMultipleFields {
}
