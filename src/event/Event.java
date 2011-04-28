package event;

/**
 * Marker Interface
 */

public interface Event {
  /**
   * Used to insert functions into the Loop. The
   * functionality implemented in the `go` method
   * is executed as soon as possible after the provided
   * timeout perios has expired.
   */
  public abstract class Timeout implements Event {
    long timeout;
    public Timeout() {}
    public Timeout(long ms) {
      this.timeout = ms;
    }

    public long getTimeout() {
      return this.timeout;
    }
    /**
     * Functionality to be executed by this timeout.
     */
    public abstract void go(TimeoutLoop l);
  }
}
