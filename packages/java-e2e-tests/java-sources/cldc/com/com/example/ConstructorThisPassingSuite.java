package com.com.example;

class AnimalService2 {
    public static void processAnimal(Animal2 animal) {
        System.out.println("Service processing animal");
        animal.makeSound();
    }
}

class Animal2 {
    public Animal2() {
        System.out.println("Animal constructor");
        AnimalService2.processAnimal(this);
    }
    
    public void makeSound() {
        System.out.println("Animal sound");
    }
}

class Dog2 extends Animal2 {
    public Dog2() {
        super();
        System.out.println("Dog constructor");
        AnimalService2.processAnimal(this);
    }
    
    public void makeSound() {
        System.out.println("Dog barks");
    }
}

public class ConstructorThisPassingSuite {
    public static void main(String[] args) {
        System.out.println("=== Test: Passing this in constructor ===");
        Dog2 dog = new Dog2();
        System.out.println("=== Test passed ===");
    }
}
