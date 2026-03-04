#!/bin/bash
# Gold patch: fix Deadlock01Bad by making thread2 acquire locks in the same
# order as thread1 (a first, then b).
#
# Original thread2:
#   b.lock();  ... a.lock();   (opposite order → deadlock)
# Fixed thread2:
#   a.lock();  ... b.lock();   (same order as thread1 → no deadlock)

cd /app

cat > src/main/java/Deadlock01Bad.java << 'JAVA'
import java.util.concurrent.locks.ReentrantLock;

public class Deadlock01Bad {
    static ReentrantLock a = new ReentrantLock();
    static ReentrantLock b = new ReentrantLock();
    static int counter = 1;

    static void thread1() {
        a.lock();
        b.lock();
        try {
            counter++;
        } finally {
            b.unlock();
            a.unlock();
        }
    }

    static void thread2() {
        a.lock();
        b.lock();
        try {
            counter--;
        } finally {
            b.unlock();
            a.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        a = new ReentrantLock();
        b = new ReentrantLock();
        Thread t1 = new Thread(() -> thread1());
        Thread t2 = new Thread(() -> thread2());

        t1.start();
        t2.start();

        t1.join();
        t2.join();
    }
}
JAVA
