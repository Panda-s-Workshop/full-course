package com.panda.fullcourse.client;

/**
 * Dev runs only: the Architectury dev launcher leaves a non-daemon thread pool behind, so after Minecraft shuts down the
 * JVM never exits (it holds the dev jars, and the next build can't replace them). Once the client is stopping, a daemon
 * thread halts the JVM after the world has had time to save. Released jars never arm it.
 */
public final class DevExit {
    /** Set by Architectury's dev launcher, absent in a real game. */
    private static final boolean DEV = System.getProperty("architectury.main.class") != null;

    private static boolean armed;

    private DevExit() {}

    /** Call when the client starts shutting down. */
    public static synchronized void onClientStopping() {
        if (!DEV || armed) return;
        armed = true;
        Thread halt = new Thread(() -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "fullcourse dev exit");
        halt.setDaemon(true);
        halt.start();
    }
}
