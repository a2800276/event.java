package event;

import java.util.TreeSet;
import java.util.SortedSet;
import java.util.Comparator;

public class TimeoutLoop extends Loop {

  private SortedSet<T> timeouts;
  private SortedSet<T> intervals;
  
  public TimeoutLoop () {
    super();
    this.timeouts  = new TreeSet<T>();
    this.intervals = new TreeSet<T>();
  }

  protected void go () {
    handleTimeouts();
    handleIntervals();
  }

  private synchronized int handleTimeouts () { 
    return handleTimeouts(this.timeouts, false);
  }
  private synchronized int handleIntervals () { 
    return handleTimeouts(this.intervals, true);
  }
  private synchronized int handleTimeouts (SortedSet<T> tos, boolean interval) {
    if (tos.size() == 0) {
      return 0;
    }

    long time = System.currentTimeMillis();
    int count = 0;
    T timeout = null;
    do {
      timeout = tos.first();
      if (time >= timeout.time) {
        timeout.ev.go();
        tos.remove(timeout);
        ++count;
        if (interval) {
          // return to queue
          addInterval(timeout.ev);
        }
        timeout = null;
      } else {
        break;
      }
    } while (0 != tos.size());

    this.maxSleep = null == timeout ? 0 : timeout.time - time;
    return count;
  } 
  
  public synchronized void addTimeout(final Event.Timeout ev) {
    addTimeout(this.timeouts, ev);
  }
  public synchronized void addInterval(final Event.Timeout ev) {
    addTimeout(this.intervals, ev);
  }


  private synchronized void addTimeout(SortedSet<T> timeouts, final Event.Timeout ev) {
    long timesOutOn = System.currentTimeMillis() + ev.getTimeout();
    timeouts.add (new T(timesOutOn, ev));
    this.maxSleep = this.maxSleep == 0 ? ev.getTimeout() : min(ev.getTimeout(), this.maxSleep);
    this.wake();
  }

  private static long min (long one, long two) {
    if (one < two) {
      return one;
    }
    return two;
  }

  public static void main(String [] args) throws Throwable {
    TimeoutLoop loop = new TimeoutLoop();
    p(loop);
    loop.start();
    
    loop.addTimeout(new Event.Timeout(750) {
      public void go () { p("timeout");}
    });


    loop.addTimeout(new Event.Timeout(850) {
      public void go () { p("timeout2");}
    });
    loop.addTimeout(new Event.Timeout(150) {
      public void go () { p("timeout0");}
    });

    loop.addInterval(new Event.Timeout(100) {
      public void go () { p("interval");}
    });
    
    Thread.sleep(1000);
    synchronized (loop) {
      loop.notify();
    }
    loop.stopLoop();

  }
  static void p (Object o) {
    System.out.println(o);
  }
  class T implements Comparable<T> {
    Event.Timeout ev;
    long time;
    T(long time, Event.Timeout ev) {
      this.time = time;
      this.ev   = ev;
    }
    public int compareTo (T o) {
      return (int)(this.time - o.time);
    }
  }
}
