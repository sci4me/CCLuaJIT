package com.sci.cclj.computer;

import com.sci.cclj.computer.util.BinarySemaphore;
import com.sci.cclj.computer.util.ReferencingCache;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

// @TODO: cancel all tasks when world unloads? or something similar

public final class TaskScheduler {
    public static final TaskScheduler INSTANCE = new TaskScheduler();

    private final Object lock = new Object();
    private final Object defaultOwner = new Object();

    private boolean running;

    private final ReferencingCache<TaskExecutor> taskExecutorCache;

    private final WeakHashMap<Object, BlockingQueue<ITask>> computerTaskQueues;
    private final Map<ITask, BlockingQueue<ITask>> taskQueues;
    private final BlockingQueue<BlockingQueue<ITask>> computerTaskQueuesActive;
    private final Set<BlockingQueue<ITask>> computerTaskQueuesActiveSet;

    private final Map<Computer, TaskExecutor> computerTaskExecutors;
    private final Map<Object, TaskExecutor> blockedTaskExecutors;
    private final BlockingQueue<TaskExecutor> taskExecutorsToFinish;

    private final BinarySemaphore dispatch;

    private TaskExecutor runningTaskExecutor;

    private TaskScheduler() {
        this.taskExecutorCache = new ReferencingCache<>(() -> new TaskExecutor(this::finish));

        this.computerTaskQueues = new WeakHashMap<>();
        this.taskQueues = new HashMap<>();
        this.computerTaskQueuesActive = new LinkedBlockingQueue<>();
        this.computerTaskQueuesActiveSet = new HashSet<>();

        this.computerTaskExecutors = new HashMap<>();
        this.blockedTaskExecutors = new HashMap<>();
        this.taskExecutorsToFinish = new LinkedBlockingQueue<>();

        this.dispatch = new BinarySemaphore();
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

        synchronized(this.lock) {
            BlockingQueue<ITask> queue = this.computerTaskQueues.get(queueObject);
            if(queue == null) {
                queue = new LinkedBlockingQueue<>();
                this.computerTaskQueues.put(queueObject, queue);
            }

            if(queue.offer(task) && !this.computerTaskQueuesActiveSet.contains(queue)) {
                this.computerTaskQueuesActive.add(queue);
                this.computerTaskQueuesActiveSet.add(queue);
            }
        }

        this.dispatch.signal();
    }

    void notifyYieldEnter(final Computer computer) {
        synchronized(this.lock) {
            final TaskExecutor taskExecutor = this.computerTaskExecutors.get(computer);
            taskExecutor.block();

            if(taskExecutor == this.runningTaskExecutor) {
                this.runningTaskExecutor = null;
            }

            this.blockedTaskExecutors.put(computer, taskExecutor);
        }

        this.dispatch.signal();
    }

    void notifyYieldExit(final Computer computer) {
        synchronized(this.lock) {
            final TaskExecutor taskExecutor = this.blockedTaskExecutors.remove(computer);
            taskExecutor.unblock();
        }

        this.dispatch.signal();
    }

    private void run() {
        try {
            while(true) {
                this.dispatch.await();

                synchronized(this.lock) {
                    if(this.runningTaskExecutor != null) continue;

                    if(this.taskExecutorsToFinish.isEmpty()) {
                        final BlockingQueue<ITask> queue = this.computerTaskQueuesActive.poll();
                        if(queue != null) {
                            final ITask task = queue.remove();
                            this.taskQueues.put(task, queue);
                            this.dispatchTask(task);
                        }
                    } else {
                        final TaskExecutor taskExecutor = this.taskExecutorsToFinish.remove();
                        this.finish(taskExecutor);
                    }
                }
            }
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void dispatchTask(final ITask task) {
        if(task == null) throw new IllegalArgumentException("task is null");

        synchronized(this.lock) {
            if(this.runningTaskExecutor != null)
                throw new IllegalStateException("Attempt to dispatch parallel ITask!");

            this.runningTaskExecutor = this.taskExecutorCache.borrowObject();

            if(this.runningTaskExecutor.isUnavailable())
                throw new IllegalStateException("Got an unavailable TaskExecutor from cache!");

            this.computerTaskExecutors.put(task.getOwner(), this.runningTaskExecutor);
            this.runningTaskExecutor.submit(task);
        }
    }

    private void finish(final TaskExecutor taskExecutor) {
        if(taskExecutor == null) throw new IllegalArgumentException("taskExecutor is null");

        synchronized(this.lock) {
            if(taskExecutor == this.runningTaskExecutor || this.runningTaskExecutor == null) {
                final BlockingQueue<ITask> queue = this.taskQueues.remove(taskExecutor.getTask());

                if(queue.isEmpty()) {
                    this.computerTaskQueuesActiveSet.remove(queue);
                } else {
                    this.computerTaskQueuesActive.add(queue);
                }

                taskExecutor.retireTask();

                this.taskExecutorCache.returnObject(taskExecutor);
                this.runningTaskExecutor = null;
            } else {
                this.taskExecutorsToFinish.add(taskExecutor);
            }
        }

        this.dispatch.signal();
    }

    private static final class TaskExecutor {
        private static int id;
        private static int runnerID;

        private final Consumer<TaskExecutor> onTaskComplete;
        private final BinarySemaphore executorSubmit;
        private final BinarySemaphore runnerSubmit;
        private final BinarySemaphore runnerFinished;

        private final Object blockedInYieldLock = new Object();
        private boolean blockedInYield;
        private ITask task;

        private Thread runnerThread;

        TaskExecutor(final Consumer<TaskExecutor> onTaskComplete) {
            this.onTaskComplete = onTaskComplete;
            this.executorSubmit = new BinarySemaphore();
            this.runnerSubmit = new BinarySemaphore();
            this.runnerFinished = new BinarySemaphore();

            final Thread executorThread = new Thread(this::execute, "CCLJ-TaskScheduler-TaskExecutorController-" + TaskExecutor.id++);
            executorThread.setDaemon(true);
            executorThread.start();
        }

        void block() {
            synchronized(this.blockedInYieldLock) {
                this.blockedInYield = true;
            }
        }

        void unblock() {
            synchronized(this.blockedInYieldLock) {
                this.blockedInYield = false;
                this.blockedInYieldLock.notify();
            }
        }

        boolean isUnavailable() {
            return this.task != null;
        }

        ITask getTask() {
            return this.task;
        }

        void submit(final ITask task) {
            if(this.isUnavailable())
                throw new IllegalStateException("Attempt to submit an ITask to a TaskExecutor that is unavailable");
            this.task = task;
            this.executorSubmit.signal();
        }

        void retireTask() {
            this.task = null;
        }

        void execute() {
            while(true) {
                try {
                    if(this.runnerThread == null) {
                        this.runnerThread = new Thread(this::run, "CCLJ-TaskScheduler-TaskExecutor-" + TaskExecutor.runnerID++);
                        this.runnerThread.setDaemon(true);
                        this.runnerThread.start();
                    }

                    this.executorSubmit.await();
                    this.runnerSubmit.signal();

                    try {
                        boolean done;
                        do {
                            done = this.runnerFinished.await(7000);

                            synchronized(this.blockedInYieldLock) {
                                if(this.blockedInYield) {
                                    continue;
                                }
                            }

                            if(!done) {
                                final Computer computer = this.task.getOwner();
                                if(computer != null) {
                                    computer.abort(false);

                                    done = this.runnerFinished.await(1500);

                                    synchronized(this.blockedInYieldLock) {
                                        if(this.blockedInYield) {
                                            continue;
                                        }
                                    }

                                    if(!done) {
                                        computer.abort(true);
                                        done = this.runnerFinished.await(1500);
                                    }
                                }

                                synchronized(this.blockedInYieldLock) {
                                    if(this.blockedInYield) {
                                        continue;
                                    }
                                }

                                if(!done) {
                                    this.runnerThread.interrupt();
                                    this.runnerThread = null;
                                    done = true;
                                }
                            }
                        } while(!done);
                    } finally {
                        while(true) {
                            synchronized(this.blockedInYieldLock) {
                                if(!this.blockedInYield) break;
                                this.blockedInYieldLock.wait();
                            }
                        }

                        this.onTaskComplete.accept(this);
                    }
                } catch(final InterruptedException ignored) {
                    // @TODO: what are we to do here?
                }
            }
        }

        void run() {
            try {
                while(true) {
                    this.runnerSubmit.await();

                    try {
                        this.task.execute();
                    } catch(final RuntimeException e) {
                        System.out.println("ComputerCraft: Error running task.");
                        e.printStackTrace();
                    }

                    this.runnerFinished.signal();
                }
            } catch(final InterruptedException e) {
                System.out.println("ComputerCraft: Error running task.");
                e.printStackTrace();
            }
        }
    }
}