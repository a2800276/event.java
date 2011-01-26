package event;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

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

  static abstract class TCPClientCB implements Callback {
    public abstract void onConnect  (Loop l, SocketChannel c);
	  public abstract void onData     (Loop l, SocketChannel c, ByteBuffer buf);
	  public abstract void write      (Loop l, ByteBuffer b);
	  public abstract void onClose    (Loop l, SocketChannel c);
    public void onError (Loop l, Throwable t) {
      l.onError(t);
    }
    public void onError    (Loop l, SocketChannel c, Throwable t){
      this.onError(l, t);
    };

  }
}
