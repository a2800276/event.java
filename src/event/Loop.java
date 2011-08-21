package event;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

/**
 * This is the basis for a java (nio) based event loop. It's meant
 * to be used to perform non-calculation intensive tasks (typically io) from
 * within a single thread.
 *
 * As a consequence, long running or blocking functions should not be executed fomr
 * within the Loop (because we're within a single thread and long-running tasks
 * would hold up everything else) and interaction with the loop should only be 
 * performed from within the loop thread itself (see @isLoopThread()).
 *
 * Tasks may be inserted into the loop using Timeouts (see @TimeoutLoop).
 */

public abstract class Loop extends Thread {
  protected long maxSleep = 0;
  volatile boolean stopped;

  protected Thread loopThread; // this is the thread running the main loop
                               // used to determine that we're not being run in another thread

  protected Callback.ErrorCallback errCB;


  protected Selector selector;
  
  public Loop () {
    try {
      this.selector = SelectorProvider.provider().openSelector();
    } catch (java.io.IOException ioe) {
      throw new RuntimeException(ioe);
    }
  };

  public Loop (Callback.ErrorCallback cb) {
    this();
    this.setErrCB(cb);
  }

 
  public boolean isRunning() {
    return !this.stopped;
  }

  public void run () {
    this.loopThread = Thread.currentThread();
    int numSelected = 0;
    while (!this.stopped) {
      try {
        // p("sel:"+this.maxSleep);
        numSelected = this.selector.select(this.maxSleep);
        if (this.stopped) {
          break;
        }
        this.maxSleep = 0; // reset maxSleep
        go();
      } catch (Throwable t) {
        onError(t);
      } 
    } 
  }
  
  /**
   * Determine whether the Thread calling this method is the same thread
   * as the one running the loop. Outside threads should not interact with
   * the loop.
   */
  public boolean isLoopThread() {
    if (this.loopThread != null && Thread.currentThread().equals(this.loopThread)) {
      return true;
    }
    return false;
  }

  protected abstract void go () throws Throwable;
  
  protected void onError(Throwable t) {
      if (null != this.errCB) {
        this.errCB.onError(this, t);
      } else {
        Callback.DEFAULT_ERROR_CB.onError(this, t);
      }
  }
  protected void onError (String msg) {
    if (null != this.errCB) {
      this.errCB.onError(this, msg);
    } else {
      Callback.DEFAULT_ERROR_CB.onError(this, msg);
    }
  }
  
  /**
   * Force the loop to run
   */
  public void wake() {
    this.selector.wakeup();
  }

  /**
   * exit the loop
   */
  public void stopLoop () {
    // this does not need to be synchronized, 
    // it will definately stop the loop eventually...
    this.stopped = true;
    this.wake();
  }
  
  /**
   *  Provide an error callback in case the loop itself runs
   *  into a problem. Default behaviour is to panic and call
   *  `System.exit()`
   */
  public void setErrCB (Callback.ErrorCallback errCB) {
    this.errCB = errCB;
  }

  static void p (Object o) {
    System.out.println(o);
  }

  static void st (String mes) {
    p(mes);
    for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
      p(e);
    }
  }

  
}
