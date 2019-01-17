package com.sci.cclj.computer;

import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public final class TaskScheduler {
    public static final TaskScheduler INSTANCE = new TaskScheduler();

    private final Object mainLock = new Object();
    private final Object defaultQueue = new Object();

    private boolean running;
    private boolean stopped;
    private Thread mainThread;

    private final WeakHashMap<Object, LinkedBlockingQueue<ITask>> computerTasks;
    private final List<LinkedBlockingQueue<ITask>> computerTasksActive;
    private final List<LinkedBlockingQueue<ITask>> computerTasksPending;
    private final Object monitor;

    private TaskScheduler() {
        this.computerTasks = new WeakHashMap<>();
        this.computerTasksActive = new ArrayList<>();
        this.computerTasksPending = new ArrayList<>();
        this.monitor = new Object();
    }

    public void start() {
        synchronized(this.mainLock) {
            if(this.running) {
                this.stopped = false;
                return;
            }

            this.mainThread = new Thread(this::run, "CCLJ-TaskScheduler-Main");
            this.mainThread.setDaemon(true);
            this.mainThread.start();

            this.running = true;
        }
    }

    public void stop() {
        synchronized(this.mainLock) {
            if(this.running) {
                this.stopped = true;
                this.mainThread.interrupt();
            }
        }
    }

    public void queueTask(final ITask task, final Computer computer) {
        Object queueObject = computer;
        if(computer == null) queueObject = this.defaultQueue;

        LinkedBlockingQueue<ITask> queue = this.computerTasks.get(queueObject);
        if(queue == null) {
            queue = new LinkedBlockingQueue<>();
            this.computerTasks.put(queueObject, queue);
        }

        synchronized(this.computerTasksPending) {
            if(queue.offer(task)) {
                if(!this.computerTasksPending.contains(queue)) {
                    this.computerTasksPending.add(queue);
                }
            } else {
                throw new RuntimeException("Event queue overflow");
            }
        }

        synchronized(this.monitor) {
            this.monitor.notify();
        }
    }

    private void run() {
        while(true) {
            synchronized(this.computerTasksPending) {
                if(!this.computerTasksPending.isEmpty()) {
                    final Iterator<LinkedBlockingQueue<ITask>> it = this.computerTasksPending.iterator();
                    while(it.hasNext()) {
                        final LinkedBlockingQueue<ITask> queue = it.next();

                        if(!this.computerTasksActive.contains(queue)) {
                            this.computerTasksActive.add(queue);
                        }

                        it.remove();
                    }
                }
            }

            final Iterator<LinkedBlockingQueue<ITask>> it = this.computerTasksActive.iterator();
            while(it.hasNext()) {
                final LinkedBlockingQueue<ITask> queue = it.next();

                if(queue == null || queue.isEmpty()) {
                    continue;
                }

                synchronized(this.mainLock) {
                    if(this.stopped) {
                        this.running = false;
                        this.mainThread = null;
                    }
                }

                try {
                    final ITask task = queue.take();

                    final Thread worker = new Thread(task::execute, "CCLJ-TaskScheduler-Worker");
                    worker.setDaemon(true);
                    worker.start();
                    worker.join(7000);

                    if(worker.isAlive()) {
                        final Computer computer = task.getOwner();

                        if(computer != null) {
                            computer.abort(false);
                            worker.join(1500);

                            if(worker.isAlive()) {
                                computer.abort(true);
                                worker.join(1500);
                            }
                        }

                        if(worker.isAlive()) {
                            worker.interrupt();
                        }
                    }
                } catch(final InterruptedException e) {
                    continue;
                }

                synchronized(queue) {
                    if(queue.isEmpty()) {
                        it.remove();
                    }
                }
            }

            while(this.computerTasksActive.isEmpty() && this.computerTasksPending.isEmpty()) {
                synchronized(this.monitor) {
                    try {
                        this.monitor.wait();
                    } catch(final InterruptedException ignored) {
                    }
                }
            }
        }
    }
}