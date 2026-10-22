package com.com.example;

public class SyncBlockBench {

  public static void main(String[] args) throws Exception {
    final int THREAD_COUNT = 4;
    final int ITERATIONS = 1000;
    final int[] counter = new int[1];
    final Object lock = new Object();

    Thread[] threads = new Thread[THREAD_COUNT];
    for (int i = 0; i < THREAD_COUNT; i++) {
      final int idx = i;
      threads[i] = new Thread(new Runnable() {
        public void run() {
          for (int j = 0; j < ITERATIONS; j++) {
            synchronized (lock) {
              counter[0]++;
            }
          }
        }
      }, "Sync-" + idx);
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

    System.out.println("counter = " + counter[0]);
  }
}
