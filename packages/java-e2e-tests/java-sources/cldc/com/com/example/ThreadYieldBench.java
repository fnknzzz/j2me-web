package com.com.example;

public class ThreadYieldBench {

  public static void main(String[] args) throws Exception {
    final int THREAD_COUNT = 8;
    final int YIELDS_PER_THREAD = 1000;
    final int[] completed = new int[1];
    final Object lock = new Object();

    Thread[] threads = new Thread[THREAD_COUNT];
    for (int i = 0; i < THREAD_COUNT; i++) {
      final int idx = i;
      threads[i] = new Thread(new Runnable() {
        public void run() {
          for (int j = 0; j < YIELDS_PER_THREAD; j++) {
            Thread.yield();
          }
          synchronized (lock) {
            completed[0]++;
          }
        }
      }, "Yielder-" + idx);
    }

    for (int i = 0; i < THREAD_COUNT; i++) {
      threads[i].start();
    }

    for (int i = 0; i < THREAD_COUNT; i++) {
      try {
        threads[i].join();
      } catch (InterruptedException e) {
      }
    }

    System.out.println("completed = " + completed[0]);
  }
}
