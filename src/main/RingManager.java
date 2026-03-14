import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RingManager (one per ring).
 *
 * Responsibilities:
 *  - Accepts tokens submitted from Main (coordinator thread).
 *  - Enforces the single-token-in-flight invariant: if the ring is idle,
 *    injects the new token immediately; otherwise enqueues it (FIFO).
 *  - When the active token completes (callback from the last RingNode), prints
 *    the completion message and injects the next queued token if any.
 *  - Shuts down cleanly after receiving a shutdown signal and draining the queue.
 *
 * Architecture:
 *  The manager itself does NOT run as its own thread; the ring nodes do the
 *  heavy lifting.  The manager is called:
 *    - from the coordinator thread  (submit / shutdown / awaitTermination)
 *    - from the last ring node thread (tokenCompleted callback)
 *  All shared state is guarded by a single ReentrantLock.
 */
public class RingManager {

    private final String   name;
    private final RingId   ringId;
    private final RingNode firstNode;   // entry point of the cycle

    // ---- state guarded by lock ----
    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition     doneSignal = lock.newCondition();

    private final Deque<Token> queue   = new ArrayDeque<>();
    private boolean            busy    = false;
    private boolean            shutdown = false;
    private boolean            terminated = false;

    // ---- threads ----
    private final RingNode[] nodes;

    public RingManager(String name, int N, int H, RingId ringId) {
        this.name   = name;
        this.ringId = ringId;

        // Build N nodes, link them in a directed cycle.
        nodes = new RingNode[N];
        for (int i = 0; i < N; i++) {
            nodes[i] = new RingNode(i, ringId, this);
        }
        for (int i = 0; i < N - 1; i++) {
            nodes[i].setNext(nodes[i + 1]);
        }
        // The last node's "next" completes and reports back to this manager,
        // which is handled inside RingNode when remainingHops reaches 0 OR
        // the token exits the last node.  We link it circularly so large H
        // values wrap around correctly.
        nodes[N - 1].setNext(nodes[0]);

        firstNode = nodes[0];
    }

    /** Start all node threads. */
    public void start() {
        for (RingNode n : nodes) {
            n.start();
        }
    }

    /**
     * Submit a token from the coordinator thread.
     * Thread-safe; may be called concurrently with tokenCompleted.
     */
    public void submit(Token token) {
        lock.lock();
        try {
            if (busy) {
                queue.addLast(token);
                System.out.printf("[%s] token %d queued  (queue size=%d)%n",
                        name, token.tokenId, queue.size());
            } else {
                inject(token);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called by a RingNode when a token has used all its hops.
     * Prints the result and injects the next queued token (if any).
     */
    void tokenCompleted(Token token) {
        System.out.printf("[%s] DONE  token_id=%d  orig=%d  final_val=%d%n",
                name, token.tokenId, token.origInput, token.currentVal);

        lock.lock();
        try {
            if (!queue.isEmpty()) {
                inject(queue.pollFirst());
            } else {
                busy = false;
                if (shutdown) {
                    // All work done; tell the nodes to stop.
                    terminateNodes();
                    terminated = true;
                    doneSignal.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Signal that no more tokens will be submitted.
     * If the ring is already idle, terminate immediately.
     */
    public void shutdown() {
        lock.lock();
        try {
            shutdown = true;
            if (!busy && queue.isEmpty()) {
                terminateNodes();
                terminated = true;
                doneSignal.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Block until all queued and in-flight work is done and nodes have exited. */
    public void awaitTermination() throws InterruptedException {
        lock.lock();
        try {
            while (!terminated) {
                doneSignal.await();
            }
        } finally {
            lock.unlock();
        }
        // Join all node threads so we're sure they've exited.
        for (RingNode n : nodes) {
            n.join();
        }
    }

    // ---- private helpers (must be called while holding lock) ----

    private void inject(Token token) {
        busy = true;
        System.out.printf("[%s] START token_id=%d  orig=%d%n",
                name, token.tokenId, token.origInput);
        firstNode.send(token);
    }

    private void terminateNodes() {
        for (RingNode n : nodes) {
            n.terminate();
        }
    }
}