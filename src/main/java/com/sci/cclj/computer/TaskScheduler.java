package com.sci.cclj.computer;

import com.sci.cclj.computer.util.BinarySemaphore;
import com.sci.cclj.computer.util.ReferencingCache;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

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
    private final BlockingQueue<TaskExecutor> toResume;

    private final BlockingQueue<TaskExecutor> toFinish;

    private final BinarySemaphore dispatch;

    private TaskExecutor runningTaskExecutor;

    private TaskScheduler() {
        this.taskExecutorCache = new ReferencingCache<>(() -> new TaskExecutor(this::finish));

        this.computerTaskQueues = new WeakHashMap<>();
        this.taskQueues = new HashMap<>();
        this.computerTaskQueuesActive = new LinkedBlockingQueue<>();
        this.computerTaskQueuesActiveSet = new HashSet<>();

        this.computerTaskExecutors = new HashMap<>();
        this.toResume = new LinkedBlockingQueue<>();

        this.toFinish = new LinkedBlockingQueue<>();

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
        }
    }

    void notifyYieldExit(final Computer computer) {
        synchronized(this.lock) {
            final TaskExecutor taskExecutor = this.computerTaskExecutors.get(computer);
            this.toResume.add(taskExecutor);
        }

        this.dispatch.signal();
    }

    private void run() {
        try {
            while(true) {
                this.dispatch.await();

                synchronized(this.lock) {
                    if(this.runningTaskExecutor != null) continue;

                    if(this.toFinish.isEmpty()) {
                        if(this.toResume.isEmpty()) {
                            final BlockingQueue<ITask> queue = this.computerTaskQueuesActive.poll();
                            if(queue != null) {
                                final ITask task = queue.remove();
                                this.taskQueues.put(task, queue);
                                this.dispatchTask(task);
                            }
                        } else {
                            final TaskExecutor taskExecutor = this.toResume.remove();
                            this.runningTaskExecutor = taskExecutor;
                            taskExecutor.unblock();
                        }
                    } else {
                        final TaskExecutor taskExecutor = this.toFinish.remove();
                        this.runningTaskExecutor = taskExecutor;
                        this.finish(taskExecutor, taskExecutor.getTask());
                    }
                }
            }
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void dispatchTask(final ITask task) {
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

    private void finish(final TaskExecutor taskExecutor, final ITask task) {
        synchronized(this.lock) {
            if(this.runningTaskExecutor == null)
                throw new IllegalStateException("Attempt to finish ITask but runningTaskExecutor is null!");

            if(taskExecutor == this.runningTaskExecutor) {
                final BlockingQueue<ITask> queue = this.taskQueues.remove(task);

                if(queue.isEmpty()) {
                    this.computerTaskQueuesActiveSet.remove(queue);
                } else {
                    this.computerTaskQueuesActive.add(queue);
                }

                this.taskExecutorCache.returnObject(taskExecutor);
                this.runningTaskExecutor = null;
            } else {
                this.toFinish.add(taskExecutor);
            }

            this.dispatch.signal();
        }
    }

    private static final class TaskExecutor {
        private static int id;

        private final BiConsumer<TaskExecutor, ITask> onTaskComplete;
        private final BinarySemaphore submit;
        private final TaskRunner runner;

        private final BinarySemaphore resume;
        private boolean blockedInYield;
        private ITask task;

        TaskExecutor(final BiConsumer<TaskExecutor, ITask> onTaskComplete) {
            this.onTaskComplete = onTaskComplete;
            this.submit = new BinarySemaphore();
            this.runner = new TaskRunner();

            this.resume = new BinarySemaphore();

            final Thread thread = new Thread(this::run, "CCLJ-TaskScheduler-TaskExecutor-" + TaskExecutor.id++);
            thread.setDaemon(true);
            thread.start();
        }

        void block() {
            this.blockedInYield = true;
        }

        void unblock() {
            this.blockedInYield = false;
            this.resume.signal();
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
            this.submit.signal();
        }

        void run() {
            try {
                while(true) {
                    this.submit.await();

                    this.runner.submit(this.task);

                    try {
                        boolean done = this.runner.await(7000);
                        if(!done) {
                            final Computer computer = this.task.getOwner();
                            if(computer != null) {
                                computer.abort(false);

                                done = this.runner.await(1500);
                                if(!done) {
                                    computer.abort(true);
                                    done = this.runner.await(1500);
                                }
                            }

                            if(!done) {
                                this.runner.interrupt();
                            }
                        }
                    } finally {
                        final ITask task = this.task;

                        while(this.blockedInYield) {
                            this.resume.await();
                        }

                        this.onTaskComplete.accept(this, task);
                        this.task = null;
                    }
                }
            } catch(final InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static final class TaskRunner {
        private static int id;

        private final Thread thread;
        private final BinarySemaphore input;
        private final BinarySemaphore finished;

        private ITask task;

        TaskRunner() {
            this.input = new BinarySemaphore();
            this.finished = new BinarySemaphore();

            this.thread = new Thread(this::run, "CCLJ-TaskScheduler-TaskRunner-" + TaskRunner.id++);
            thread.setDaemon(true);
            thread.start();
        }

        void submit(final ITask task) {
            if(this.task != null)
                throw new IllegalStateException("Attempt to submit an ITask to a TaskRunner that is unavailable");
            this.task = task;
            this.input.signal();
        }

        boolean await(final long timeout) throws InterruptedException {
            return this.finished.await(timeout);
        }

        void interrupt() {
            this.task = null;
            this.thread.interrupt();
        }

        void run() {
            try {
                while(true) {
                    this.input.await();
                    try {
                        this.task.execute();
                    } catch(final Throwable t) {
                        throw new RuntimeException("Error running task", t);
                    }
                    this.task = null;
                    this.finished.signal();
                }
            } catch(final InterruptedException ignored) {
                // @TODO ?
            }
        }
    }
}