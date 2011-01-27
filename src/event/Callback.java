package event;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;

public interface Callback {
  
  public ErrorCallback DEFAULT_ERROR_CB = new DefaultErrorCallback();

  static interface ErrorCallback extends Callback {
    public void onError (Loop l, Throwable t);
  }

  static class DefaultErrorCallback implements ErrorCallback {
    public void onError (Loop l, Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  static abstract class TCPClientCB implements ErrorCallback {
    public abstract void onConnect  (TCPClientLoop l, SocketChannel c);
	  public abstract void onData     (TCPClientLoop l, SocketChannel c, ByteBuffer buf);
	  public abstract void onClose    (TCPClientLoop l, SocketChannel c);

	  //public void write (TCPClientLoop l, SocketChannel c, ByteBuffer buf) {
    //  l.queueOp(c, SelectionKey.OP_WRITE, this, buf);
    //};

    public void onError (Loop l, Throwable t) {
      l.onError(t);
    }
    public void onError (TCPClientLoop l, SocketChannel c, Throwable t){
      this.onError(l, t);
    };

  }
}
