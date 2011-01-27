package event;


import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

public abstract class Loop extends Thread {
  protected long maxSleep = 0;
  private boolean stopped;

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
    int numSelected = 0;
    this.loopThread = Thread.currentThread();
    while (!stopped) {
      try {
        numSelected = this.selector.select(this.maxSleep);
        go();
      } catch (Throwable t) {
        onError(t);
      } 
    } 
  }

  protected abstract void go () throws Throwable;
  
  protected void onError(Throwable t) {
      if (null != this.errCB) {
        this.errCB.onError(this, t);
      } else {
        Callback.DEFAULT_ERROR_CB.onError(this, t);
      }
  }
  

  public void wake() {
    this.selector.wakeup();
  }
  public synchronized void stopLoop () {
    this.stopped = true;
  }

  public void setErrCB (Callback.ErrorCallback errCB) {
    this.errCB = errCB;
  }

  static void p (Object o) {
    System.out.println(o);
  }

  
}
