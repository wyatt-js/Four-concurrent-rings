import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/**
 * JvmMon — periodic JVM health reporter, analogous to BeamMon in Elixir.
 *
 * Prints every intervalMs milliseconds:
 *   - Heap used / committed / max  (in MB)
 *   - Live thread count
 *   - Peak thread count (since JVM start or last reset)
 */
public class JvmMon {

    private static Thread monitorThread;
    private static volatile boolean running = false;

    public static void start(long intervalMs) {
        running = true;
        monitorThread = new Thread(() -> {
            MemoryMXBean  mem     = ManagementFactory.getMemoryMXBean();
            ThreadMXBean  threads = ManagementFactory.getThreadMXBean();

            // Print once immediately, then every interval
            boolean first = true;
            while (running) {
                if (!first) {
                    try { Thread.sleep(intervalMs); } catch (InterruptedException e) { return; }
                }
                first = false;

                long heapUsed      = mem.getHeapMemoryUsage().getUsed()      / (1024 * 1024);
                long heapCommitted = mem.getHeapMemoryUsage().getCommitted()  / (1024 * 1024);
                long heapMax       = mem.getHeapMemoryUsage().getMax()        / (1024 * 1024);
                int  liveThreads   = threads.getThreadCount();
                int  peakThreads   = threads.getPeakThreadCount();

                System.out.printf("[JVM] heap=%dMB/%dMB/%dMB  threads(live=%d peak=%d)%n",
                        heapUsed, heapCommitted, heapMax, liveThreads, peakThreads);
            }
        }, "jvm-mon");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public static void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }
}