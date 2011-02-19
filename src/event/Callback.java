package event;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SelectionKey;

public interface Callback {
  
  public ErrorCallback DEFAULT_ERROR_CB = new DefaultErrorCallback();

  static interface ErrorCallback extends Callback {
    public void onError (Loop l, Throwable t);
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

  static abstract class TCPClientCB implements ErrorCallback {
    /* default impl is noop, you may want to override this to do 
     * initialization */
    public void onConnect  (TCPClientLoop l, SocketChannel c) {}
	  public abstract void onData     (TCPClientLoop l, SocketChannel c, ByteBuffer buf);
    /* default impl is noop, you may want to override this to do 
     * cleanup */
	  public  void onClose    (TCPClientLoop l, SocketChannel c) {}

	  //public void write (TCPClientLoop l, SocketChannel c, ByteBuffer buf) {
    //  l.queueOp(c, SelectionKey.OP_WRITE, this, buf);
    //};

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

}
