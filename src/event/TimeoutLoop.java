package event;

import java.util.Queue;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.Comparator;


/**
 * Minimal Loop implementation does something. This is basically
 * a simple scheduler that executes tasks (currently not very aptly 
 * named Event.Timeout) on or after a specified time in the future.
 *
 * This may occur once (addTimeout) or as an interval (addInterval)
 *
 * Usage:
 *
 *   TimeoutLoop l = new TimeoutLoop();
 *               l.addTimeout ( new Event.Timeout(1000) {
 *                 public void go() { 
 *                  // do something. Whatever you define to be
 *                  // done here will be executed in about 1 second.
 *                 }
 *               });
 *               l.run();
 *
 *               l.stopLoop();
 */

public class TimeoutLoop extends Loop {
  
  //
  // Stores the non expired timeout functions until
  // they expire and are executed.
  //
  protected Queue <T> timeouts;

  // 
  // Stores new Timeouts until they can be safely transfered
  // to the `timouts` queue (see above)
  //
  protected LinkedList<T> newTimeouts;
  
  /**
   * reference time for the current iteration of the loop
   */
  long loopTime;  


  public TimeoutLoop () {
    super();
    this.timeouts    = new PriorityQueue<T>();
    this.newTimeouts = new LinkedList<T>();
  }

  void dump (String mes) {
    p(mes);
    p("thr: "+Thread.currentThread().getId());
    p("lth: "+this.loopThread.getId());
    p("new: "+newTimeouts);
    p("tos: "+timeouts);
    p("run: "+maxSleep);
  }

  protected void go () {
    assert this.isLoopThread();

    this.loopTime = System.nanoTime();
    handleTimeouts();
    
    // New timeouts need to handled after current
    // timeouts because they may have been added to the 
    // queue by a timeout that ran in the current 
    // iteration. 
    handleNewTimeouts();
  }

  private void handleNewTimeouts() {
    assert this.isLoopThread();
    
    // why synchronized? see @addTimeout, below...
    synchronized(this.newTimeouts){
      this.timeouts.addAll(this.newTimeouts);
      setMaxSleep();

      this.newTimeouts.clear();
    }
  }

  //
  // Calculate the maximum time the loop is allowed to sleep for
  // depending on the next timeout to expire ...
  // 
  private void setMaxSleep() {
    assert this.isLoopThread();

    T timeout = this.timeouts.peek();

    long sleep = (null == timeout ? 0 : max(1000000, timeout.time - this.loopTime));
         sleep /= 1000000;
    
    //
    // sleep of 0 is "until woken"
    //

    this.maxSleep = sleep;
  }
  
  //
  // execute all task who's time has come...
  //
  private int handleTimeouts () { 
    assert this.isLoopThread();

    if (this.timeouts.size() == 0) {
      return 0;
    }

    int count = 0;
    T timeout = null;

    do {
      timeout = this.timeouts.peek();
      if (this.loopTime >= timeout.time) {
        timeout.ev.go(this);
        this.timeouts.remove(timeout);
        ++count;
        if (timeout.interval) { // return intervals to queue
          timeout.time = this.loopTime + (timeout.ev.getTimeout()*1000000);
          this.timeouts.add(timeout);
        }
      } else {
        break;
      }
    } while (0 != this.timeouts.size());
    return count;
  } 
  
  /**
   * Thread safe method to inject functionality into the loop
   */
  public long addTimeout(final Event.Timeout ev) {
    return addTimeout(ev, false);
  }

  /**
   * Thread safe method to inject repeating functionality into the loop
   */
  public long addInterval(final Event.Timeout ev) {
    return addTimeout(ev, true);
  }
  
  /**
   * Cancel the execution of the timeout.
   */
  public void cancelTimeout(final long id) {
    if (!this.isLoopThread()) {
      this.addTimeout(new Event.Timeout() {
        public void go (TimeoutLoop l) {
          l.cancelTimeout(id);
        } 
      });
      return;
    }
    for (T t : this.timeouts) {
      if (t.id == id) {
        this.timeouts.remove(t);
        return;
      }
    }
    //
    // if the event was added AND cancelled from within the
    // loop, it may still be in the newTimeouts.
    //
    
    for (T t : this.newTimeouts) {
      if (t.id == id) {
        this.newTimeouts.remove(t);
        return;
      }
    }
    
  }


  private long addTimeout(final Event.Timeout ev, boolean interval) {

    long timesOutOn = System.nanoTime() + (ev.getTimeout()*1000000);
    T t = null;
    synchronized (this.newTimeouts) {
      t = new T(timesOutOn, ev, interval);
      // even if we are in the loop thread,
      // we have to add a new timeout to the
      // new timeout queue (to be transfered to
      // the proper timeout queue in the next the
      // iteration) instead of putting it
      // directly on the proper timeout queue, because
      // else the timeout loop `handleTimeouts` would
      // run indefinitely if an `immediate` timeout
      // is added from within the loop thread. E.g.:
      // 
      // new Event.Timeout() {
      //  public void go(TimeoutLoop loop) {
      //    loop.addTimeout(this);
      //  }
      // }
      this.newTimeouts.add (t);
    }
    if (!this.isLoopThread()) {
      //
      // make sure an indefinately sleeping loop is
      // aware of a new timeout.
      //
      this.wake();
    }
    return t.id;
  }

  private static long min (long one, long two) {
    if (one < two) {
      return one;
    }
    return two;
  }
  private static long max (long one, long two) {
    if (one > two) {
      return one;
    }
    return two;
  }


  public static void main(String [] args) throws Throwable {
    TimeoutLoop loop = new TimeoutLoop();
    //p(loop);
    
    loop.start();
    loop.addTimeout(new Event.Timeout(750) {
      public void go (TimeoutLoop l) { p("timeout");}
    });

    loop.addTimeout(new Event.Timeout() {
      public void go (TimeoutLoop l) { p("timeout-1");}
    });

    loop.addTimeout(new Event.Timeout(850) {
      public void go (TimeoutLoop l) { p("timeout2");}
    });

    loop.addTimeout(new Event.Timeout(150) {
      int i;
      public void go (TimeoutLoop l) { 
        p("timeout0");
        i++;
        if (i>3) return;
        ((TimeoutLoop)l).addTimeout(this);
      }
    });

    loop.addInterval(new Event.Timeout(100) {
      public void go (TimeoutLoop l) { p("interval");}
    });
    
    Thread.sleep(1000);
    loop.stopLoop();

  }
  static void p (Object o) {
    System.out.println(o);
  }

  long idSeq;

  class T implements Comparable<T> {
    long id;
    Event.Timeout ev;
    long time;
    boolean interval;
    T(long time, Event.Timeout ev, boolean interval) {
      this.time = time;
      this.ev   = ev;
      this.interval = interval;
      this.id = TimeoutLoop.this.idSeq++;
    }
    public int compareTo (T o) {
      return (int)(this.time - o.time);
    }
  }
}
