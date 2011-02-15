package event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SelectionKey;
import java.net.SocketAddress;

public class TCPServerLoop extends TCPClientLoop {
  public TCPServerLoop() {
    super();
  }
  
  /**
   * IOException
   * java.nio.channels.ClosedChannelException
   */
  public void createTCPServer (final Callback.TCPServerCB cb, SocketAddress sa) {
    try {
      final ServerSocketChannel ssc = ServerSocketChannel.open();
      ssc.configureBlocking(false);
      ssc.socket().bind(sa);
      cb.onConnect(this, ssc);
      if (this.isLoopThread()) {
        ssc.register(this.selector, SelectionKey.OP_ACCEPT, cb);
      } else {
        this.addTimeout(new Event.Timeout(){
          public void go(TimeoutLoop l) {
            try {
              ssc.register(l.selector, SelectionKey.OP_ACCEPT, cb);
            } catch (java.nio.channels.ClosedChannelException cce) {
              cb.onError((TCPServerLoop)l, ssc, cce);
            }
          }
        }); 
      }
    } catch (java.io.IOException ioe) {
      cb.onError(this, ioe);
    }
  }

}
