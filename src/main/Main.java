import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
 
/**
 * Main coordinator / entry point.
 *
 * Reads N and H from the user, starts the four rings, then loops reading
 * integers (or "done") and routing each to the correct RingManager.
 */
public class Main {
 
    private static final AtomicInteger tokenIdCounter = new AtomicInteger(0);
 
    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
 
        System.out.print("Enter N (ring size, nodes per ring): ");
        int N = Integer.parseInt(scanner.nextLine().trim());
 
        System.out.print("Enter H (hops per token): ");
        int H = Integer.parseInt(scanner.nextLine().trim());
 
        System.out.printf("Starting four rings  N=%d  H=%d%n%n", N, H);
 
        // Start the JVM monitor (analogous to BeamMon)
        JvmMon.start(5_000);
 
        // Create the four ring managers
        RingManager negRing      = new RingManager("NEG",      N, H, RingId.NEG);
        RingManager zeroRing     = new RingManager("ZERO",     N, H, RingId.ZERO);
        RingManager posEvenRing  = new RingManager("POS_EVEN", N, H, RingId.POS_EVEN);
        RingManager posOddRing   = new RingManager("POS_ODD",  N, H, RingId.POS_ODD);
 
        negRing.start();
        zeroRing.start();
        posEvenRing.start();
        posOddRing.start();
 
        System.out.println("Type integers to process, or 'done' to quit.");
 
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
 
            if (line.equalsIgnoreCase("done")) {
                System.out.println("Shutting down — waiting for queued work to finish...");
                break;
            }
 
            try {
                long x = Long.parseLong(line);
                int tokenId = tokenIdCounter.incrementAndGet();
                Token token = new Token(tokenId, route(x), x, x, H);
 
                RingManager target = selectRing(x, negRing, zeroRing, posEvenRing, posOddRing);
                target.submit(token);
 
            } catch (NumberFormatException e) {
                System.out.println("Error: not a valid integer.");
            }
        }
 
        // Signal all rings to shut down after draining their queues
        negRing.shutdown();
        zeroRing.shutdown();
        posEvenRing.shutdown();
        posOddRing.shutdown();
 
        negRing.awaitTermination();
        zeroRing.awaitTermination();
        posEvenRing.awaitTermination();
        posOddRing.awaitTermination();
 
        JvmMon.stop();
        System.out.println("All rings finished. Goodbye.");
    }
 
    private static RingId route(long x) {
        if (x < 0)               return RingId.NEG;
        if (x == 0)              return RingId.ZERO;
        if (x % 2 == 0)          return RingId.POS_EVEN;
        return RingId.POS_ODD;
    }
 
    private static RingManager selectRing(long x,
                                          RingManager neg,
                                          RingManager zero,
                                          RingManager posEven,
                                          RingManager posOdd) {
        return switch (route(x)) {
            case NEG      -> neg;
            case ZERO     -> zero;
            case POS_EVEN -> posEven;
            case POS_ODD  -> posOdd;
        };
    }
}