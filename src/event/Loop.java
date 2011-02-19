package event;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

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

  public void run () {
    this.loopThread = Thread.currentThread();
    int numSelected = 0;
    while (!stopped) {
      try {
        // p("sel:"+this.maxSleep);
        numSelected = this.selector.select(this.maxSleep);
        this.maxSleep = 0; // reset maxSleep
        go();
      } catch (Throwable t) {
        onError(t);
      } 
    } 
  }

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
   * force the loop to run
   */
  public void wake() {
    this.selector.wakeup();
  }

  /**
   * exit the loop
   */
  public void stopLoop () {
    this.stopped = true;
    this.wake();
  }

  public void setErrCB (Callback.ErrorCallback errCB) {
    this.errCB = errCB;
  }

  static void p (Object o) {
    System.out.println(o);
  }

  
}
