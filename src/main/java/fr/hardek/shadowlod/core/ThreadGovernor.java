package fr.hardek.shadowlod.core;

import fr.hardek.shadowlod.ShadowLOD;

import java.util.Locale;

public final class ThreadGovernor {
    private static volatile boolean started;

    public static void start() {
        if (started) return;
        started = true;
        Thread t = new Thread(ThreadGovernor::loop, "ShadowLOD Thread Governor");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void loop() {
        Thread[] buf = new Thread[512];
        while (true) {
            try {
                Thread.sleep(3000L);
                if (!Settings.threadGovernor) continue;
                ThreadGroup root = Thread.currentThread().getThreadGroup();
                while (root.getParent() != null) root = root.getParent();
                int n = root.enumerate(buf, true);
                if (n == buf.length) buf = new Thread[buf.length * 2];
                for (int i = 0; i < n; i++) {
                    Thread th = buf[i];
                    if (th == null) continue;
                    String name = th.getName();
                    if (name.equals("Render thread")) {
                        if (th.getPriority() != Thread.MAX_PRIORITY) th.setPriority(Thread.MAX_PRIORITY);
                        continue;
                    }
                    if (matches(name) && th.getPriority() != Thread.MIN_PRIORITY) {
                        th.setPriority(Thread.MIN_PRIORITY);
                    }
                    buf[i] = null;
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                ShadowLOD.LOG.debug("[ShadowLOD] thread governor", t);
            }
        }
    }

    private static boolean matches(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String p : Settings.lowPriorityThreads) {
            if (!p.isEmpty() && n.contains(p)) return true;
        }
        return false;
    }

    private ThreadGovernor() {}
}
