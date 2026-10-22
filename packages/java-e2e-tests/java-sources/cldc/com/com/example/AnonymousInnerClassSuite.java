package com.com.example;

interface Callback {
    void onEvent(String message);
}

class EventEmitter {
    private Callback callback;
    
    public void setCallback(Callback cb) {
        this.callback = cb;
    }
    
    public void trigger(String msg) {
        if (this.callback != null) {
            this.callback.onEvent(msg);
        }
    }
}

public class AnonymousInnerClassSuite {
    public static void main(String[] args) {
        System.out.println("=== Anonymous Inner Class Test ===");
        
        EventEmitter emitter = new EventEmitter();
        
        Callback callback = new Callback() {
            public void onEvent(String message) {
                System.out.print("Callback received: ");
                System.out.println(message);
            }
        };
        
        emitter.setCallback(callback);
        System.out.println("Callback set");
        
        emitter.trigger("Event 1");
        emitter.trigger("Event 2");
        
        System.out.println("Test completed");
    }
}
