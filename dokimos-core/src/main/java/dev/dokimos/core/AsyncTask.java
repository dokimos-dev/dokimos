package dev.dokimos.core;

import java.util.concurrent.CompletableFuture;

/**
 * A non-blocking task that produces a {@link TaskResult} asynchronously.
 * <p>
 * {@code AsyncTask} is the asynchronous counterpart to {@link MeasuredTask}: it returns a
 * {@link CompletableFuture} so suspend-based or reactive callers (for example Koog agents or
 * Reactor pipelines) can drive an experiment without blocking a thread per example. The completed
 * future carries the same {@link TaskResult} carrier used by the synchronous paths, so call
 * metrics flow through to each {@link ItemResult} exactly as they do for {@link MeasuredTask}.
 * <p>
 * Set an async task on an experiment via {@link Experiment.Builder#asyncTask(AsyncTask)}. When an
 * async task is configured the experiment runs through a dedicated bounded async execution path
 * (see {@link Experiment.Builder#parallelism(int)} for the in-flight cap); otherwise the
 * synchronous sequential or parallel paths are used.
 */
@FunctionalInterface
public interface AsyncTask {

    /**
     * Runs the task on the given {@link Example} and returns a future of its result.
     *
     * @param example the example containing inputs and expected outputs
     * @return a future that completes with the task result, or completes exceptionally if the task
     *     fails (a failed future is isolated as a failed {@link ItemResult}, the run continues)
     */
    CompletableFuture<TaskResult> run(Example example);
}
