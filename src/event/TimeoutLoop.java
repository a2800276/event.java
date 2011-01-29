package event;

import java.util.TreeSet;
import java.util.SortedSet;
import java.util.LinkedList;
import java.util.Comparator;


public class TimeoutLoop extends Loop {

  private SortedSet <T> timeouts;
  private LinkedList<T> newTimeouts;
  
  volatile boolean hasNewTO;


  public TimeoutLoop () {
    super();
    this.timeouts    = new TreeSet<T>();
    this.newTimeouts = new LinkedList<T>();
  }

  protected void go () {
    handleNewTimeouts();
    handleTimeouts();
  }

  private void handleNewTimeouts() {
    if (!this.hasNewTO) {
      return;
    }
    synchronized(this.newTimeouts){
      this.timeouts.addAll(this.newTimeouts);
      this.newTimeouts.clear();
      this.hasNewTO = false;
    }
  }

  private int handleTimeouts () { 
    //long time = System.currentTimeMillis();
    long time = System.nanoTime();
    int count = 0;
    T timeout = null;

    if (this.timeouts.size() == 0) {
      return 0;
    }
    do {
      timeout = this.timeouts.first();
      if (time >= timeout.time) {
        timeout.ev.go(this);
        this.timeouts.remove(timeout);
        ++count;
        if (timeout.interval) { // return to queue
          timeout.time = time + (timeout.ev.getTimeout()*1000000);
          this.timeouts.add(timeout);
        }
        timeout = null;
      } else {
        break;
      }
    } while (0 != this.timeouts.size());
  
    this.maxSleep = null == timeout ? 0 : max(1000000, timeout.time - time);
    this.maxSleep /= 1000000;
//          p("t-t:"+(timeout.time - time));
//          p("tosize:"+this.timeouts.size());
//          p("to:"+timeout);
//    p("set:"+this.maxSleep);
    return count;
  } 
  // create mechanism to introduce new TO's and Intervals to the
  // loop to avoid synch unless something is being added ... cf createTCPClient
  public void addTimeout(final Event.Timeout ev) {
    addTimeout(ev, false);
  }
  public void addInterval(final Event.Timeout ev) {
    addTimeout(ev, true);
  }


  private void addTimeout(final Event.Timeout ev, boolean interval) {
    //long timesOutOn = System.currentTimeMillis() + ev.getTimeout();
    long timesOutOn = System.nanoTime() + (ev.getTimeout()*1000000);
    T t = new T(timesOutOn, ev, interval);
    
    if (this.isLoopThread()) {
      this.timeouts.add(t);
    } else {
      synchronized (this.newTimeouts) {
        this.newTimeouts.add (t);
        this.hasNewTO = true;
      }
      this.wake();
    }
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

    loop.addTimeout(new Event.Timeout(0) {
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
  class T implements Comparable<T> {
    Event.Timeout ev;
    long time;
    boolean interval;
    T(long time, Event.Timeout ev, boolean interval) {
      this.time = time;
      this.ev   = ev;
      this.interval = interval;
    }
    public int compareTo (T o) {
      return (int)(this.time - o.time);
    }
  }
}
