package event;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SelectionKey;

/**
 * This interface defines some interaction with the event loop
 */

public interface Callback {
  /**
   *  Default callback to handle loop errors.
   */
  public static final ErrorCallback DEFAULT_ERROR_CB = new DefaultErrorCallback();
  
  /**
   *  Interface to define how errors are handled in the Loop.
   */
  static interface ErrorCallback extends Callback {
    /**
     *  Functionality to handle Exceptions.
     */
    public void onError (Loop l, Throwable t);
    /**
     *  Functionality to handle String based errors.
     */
    public void onError (Loop l, String msg);
  }

  static class DefaultErrorCallback implements ErrorCallback {
    public void onError (Loop l, Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
    public void onError(Loop l, String msg) {
      System.err.println(msg);
      Thread.currentThread().dumpStack();
      System.exit(1);
    }
  }
  
  /**
   * Basic implementation of the callbacks that need to be handled
   * when implementing a TCP client
   */
  static abstract class TCPClientCB implements ErrorCallback {
    /**
     * Functionality to be executed with a connection is established on this 
     * client,  default impl is noop, you may want to override this to do 
     * initialization 
     */
    public void onConnect  (TCPClientLoop l, SocketChannel c) {}
    /**
     *  Functionality that is to be executed when the data is received
     *  by this client.
     */
	  public abstract void onData     (TCPClientLoop l, SocketChannel c, ByteBuffer buf);
    /**
     * Functionality to be executed when the client connection is closed.
     * default impl is noop, you may want to override this to do 
     * cleanup 
     */
	  public  void onClose    (TCPClientLoop l, SocketChannel c) {}

	  //public void write (TCPClientLoop l, SocketChannel c, ByteBuffer buf) {
    //  l.queueOp(c, SelectionKey.OP_WRITE, this, buf);
    //};
    
    // default error handlers hand off handlings errors to the loop,
    // this may not be what you want, as the loop's default error
    // handlers shut down the vm.

    public void onError (Loop l, Throwable t) {
      l.onError(t);
    }
    public void onError (Loop l, String msg) {
      l.onError(msg);  
    }
    public void onError (TCPClientLoop l, SocketChannel c, Throwable t){
      this.onError(l, t);
    };
  }
  
  static abstract class TCPServerCB implements ErrorCallback {
    // useful ? prbly not. currently noop, override for ???
    // may disappear
    public  void onConnect (TCPServerLoop l, ServerSocketChannel ssc){}
    public abstract void onAccept (TCPServerLoop l, ServerSocketChannel ssc, SocketChannel sc);
    //
    // perhaps onShutdown ?
    // default impl is noop, override for cleanup
    public  void onClose (TCPServerLoop l, ServerSocketChannel ssc){}
    public void onError(Loop l, Throwable t) {
      l.onError(t);
    }
    public void onError (Loop l, String msg) {
      l.onError(msg);  
    }
    public void onError(TCPServerLoop l, ServerSocketChannel ssc, Throwable t) {
      this.onError(l, t);
    }
  }

  /**
   * Used to insert functions into the Loop. The
   * functionality implemented in the `go` method
   * is executed as soon as possible after the provided
   * timeout perios has expired.
   */
  public abstract class Timeout implements Callback {
    public Timeout() {}

    /**
     * Functionality to be executed by this timeout.
     */
    public abstract void go(TimeoutLoop l);
  }


}
