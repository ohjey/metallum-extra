package com.metallumextra;

import com.sun.management.GarbageCollectionNotificationInfo;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects stop-the-world GC pause time from JMX notifications (arrives on a JMX thread),
 * which the render thread drains once per frame.
 */
final class GcMonitor {
    private static final AtomicLong pendingPauseMs = new AtomicLong();
    private static final AtomicInteger pendingPauses = new AtomicInteger();
    private static volatile String lastCollector = "";
    private static boolean installed;

    private GcMonitor() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                String name = bean.getName();
                // "Cycles"/"Concurrent" beans report concurrent work that does NOT pause the game.
                if (name.contains("Cycles") || name.contains("Concurrent")) {
                    continue;
                }
                if (bean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener((notification, handback) -> {
                        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                            return;
                        }
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                        pendingPauseMs.addAndGet(info.getGcInfo().getDuration());
                        pendingPauses.incrementAndGet();
                        lastCollector = info.getGcName() + "/" + info.getGcCause();
                    }, null, null);
                }
            }
        } catch (Throwable t) {
            MetallumExtra.LOGGER.warn("[Metallum Extra] GC pause tracking unavailable on this Java runtime: {}", t.toString());
        }
    }

    /** Returns GC pause milliseconds since the last call. */
    static long drainPauseMs() {
        return pendingPauseMs.getAndSet(0L);
    }

    static int drainPauseCount() {
        return pendingPauses.getAndSet(0);
    }

    static String lastCollector() {
        return lastCollector;
    }
}
