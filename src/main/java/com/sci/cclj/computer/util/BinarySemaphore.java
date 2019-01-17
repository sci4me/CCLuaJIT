package com.sci.cclj.computer.util;

public final class BinarySemaphore {
    private volatile boolean state;

    public synchronized void signal() {
        this.state = true;
        this.notify();
    }

    public synchronized void await() throws InterruptedException {
        while(!this.state) this.wait();
        this.state = false;
    }

    public synchronized boolean await(final long timeout) throws InterruptedException {
        if(!this.state) {
            this.wait(timeout);
            if(!this.state) return false;
        }
        this.state = false;
        return true;
    }
}