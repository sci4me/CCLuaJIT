package com.sci.cclj.computer;

import com.sci.cclj.computer.util.ReferencingCache;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class TaskScheduler {
    public static final TaskScheduler INSTANCE = new TaskScheduler();

    private final Object lock = new Object();
    private final Object defaultOwner = new Object();

    private boolean running;

    private final ReferencingCache<TaskExecutor> taskExecutorCache;

    private final WeakHashMap<Object, BlockingQueue<ITask>> computerTaskQueues;
    private final BlockingQueue<BlockingQueue<ITask>> computerTaskQueuesActive;
    private final Set<BlockingQueue<ITask>> computerTaskQueuesActiveSet;

    private final BlockingQueue<TaskExecutor> blockedTaskExecutors;

    private TaskScheduler() {
        this.taskExecutorCache = new ReferencingCache<>(TaskExecutor::new);

        this.computerTaskQueues = new WeakHashMap<>();
        this.computerTaskQueuesActive = new LinkedBlockingQueue<>();
        this.computerTaskQueuesActiveSet = new HashSet<>();

        this.blockedTaskExecutors = new LinkedBlockingQueue<>();
    }

    public void start() {
        synchronized(this.lock) {
            if(this.running) return;
            this.running = true;
        }

        final Thread dispatcherThread = new Thread(this::run, "CCLJ-TaskScheduler-Dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    public void stop() {
        throw new RuntimeException("Unexpected call to TaskScheduler::stop");
    }

    public void queueTask(final ITask task, final Computer computer) {
        Object queueObject = computer;
        if(computer == null) queueObject = this.defaultOwner;

        BlockingQueue<ITask> queue;
        synchronized(this.computerTaskQueues) {
            queue = this.computerTaskQueues.get(queueObject);
            if(queue == null) {
                queue = new LinkedBlockingQueue<>();
                this.computerTaskQueues.put(queueObject, queue);
            }
        }

        synchronized(this.lock) {
            if(queue.offer(task) && !this.computerTaskQueuesActiveSet.contains(queue)) {
                this.computerTaskQueuesActive.add(queue);
                this.computerTaskQueuesActiveSet.add(queue);
            }
        }
    }

    private void run() {

    }

    private static final class TaskExecutor implements Runnable {
        private final TaskRunner runner;

        TaskExecutor() {
            this.runner = new TaskRunner();
        }

        @Override
        public void run() {

        }
    }

    private static final class TaskRunner implements Runnable {
        @Override
        public void run() {

        }
    }
}