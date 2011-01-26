package event;

/**
 * Marker Interface
 */

public interface Event {
  public abstract class Timeout implements Event {
    long timeout;
    public Timeout(long ms) {
      this.timeout = ms;
    }

    public long getTimeout() {
      return this.timeout;
    }
    public abstract void go();
  }


}
