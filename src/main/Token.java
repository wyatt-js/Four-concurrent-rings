/**
 * Immutable envelope passed between ring nodes.
 *
 * currentVal and remainingHops are mutable during processing; all other
 * fields are set once at creation and never change.
 */
public class Token {

    public final int   tokenId;
    public final RingId ringId;
    public final long  origInput;

    // mutable during a single ring traversal
    public long currentVal;
    public int  remainingHops;

    public Token(int tokenId, RingId ringId, long origInput, long currentVal, int remainingHops) {
        this.tokenId       = tokenId;
        this.ringId        = ringId;
        this.origInput     = origInput;
        this.currentVal    = currentVal;
        this.remainingHops = remainingHops;
    }

    @Override
    public String toString() {
        return String.format("Token{id=%d ring=%s orig=%d final=%d}",
                tokenId, ringId, origInput, currentVal);
    }
}