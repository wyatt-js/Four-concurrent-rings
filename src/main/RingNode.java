import java.util.concurrent.SynchronousQueue;

/**
 * RingNode represents one node (thread) in a directed ring.
 *
 * Each node blocks on a SynchronousQueue waiting for a token.
 * When it receives one it:
 *   1. Applies the ring-specific transformation.
 *   2. Decrements remainingHops.
 *   3a. If hops remain, forwards the token to the next node.
 *   3b. If hops are exhausted, reports completion to the RingManager.
 *
 * A null sentinel sent via send() signals the node to exit cleanly.
 *
 * Arithmetic uses Java long (signed 64-bit wraparound for free).
 */
public class RingNode extends Thread {

    private final int         index;
    private final RingId      ringId;
    private final RingManager manager;

    private RingNode next;

    // SynchronousQueue gives a clean hand-off: the sender blocks until the
    // receiver picks up, which matches the "pass a message to the next node"
    // semantics perfectly without needing an explicit buffer.
    private final SynchronousQueue<Token> inbox = new SynchronousQueue<>();

    public RingNode(int index, RingId ringId, RingManager manager) {
        this.index   = index;
        this.ringId  = ringId;
        this.manager = manager;
        setDaemon(false);
        setName(ringId + "-node-" + index);
    }

    public void setNext(RingNode next) {
        this.next = next;
    }

    /** Hand a token to this node (blocks until the node accepts). */
    public void send(Token token) {
        try {
            inbox.put(token);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Send the null sentinel to make the node's run() loop exit. */
    public void terminate() {
        try {
            inbox.put(new Token(-1, ringId, 0, 0, 0)); // sentinel
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (true) {
            Token token;
            try {
                token = inbox.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Sentinel check
            if (token.tokenId == -1) {
                return;
            }

            // Apply transformation
            token.currentVal = transform(token.currentVal);
            token.remainingHops--;

            if (token.remainingHops > 0) {
                next.send(token);
            } else {
                manager.tokenCompleted(token);
            }
        }
    }

    // ---- ring transformations (signed 64-bit; Java long wraps automatically) ----

    private long transform(long v) {
        return switch (ringId) {
            case NEG      -> v * 3L + 1L;
            case ZERO     -> v + 7L;
            case POS_EVEN -> v * 101L;
            case POS_ODD  -> v * 101L + 1L;
        };
    }
}