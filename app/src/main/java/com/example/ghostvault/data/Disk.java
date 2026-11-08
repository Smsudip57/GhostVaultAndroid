package com.example.ghostvault.data;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Room operations must never run on the main/UI thread. */
public final class Disk {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private Disk() {}
    public static void run(Runnable task) { IO.execute(task); }
}
