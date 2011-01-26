package event;

import java.util.Queue;
import java.util.LinkedList;

import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketAddress;

public class TCPClientLoop extends TimeoutLoop {
  
  Queue<R> registerOpsQueue;

  public TCPClientLoop () {
    super();
    registerOpsQueue = new LinkedList<R>();
  }

  public void createTCPClient (Callback.TCPClientCB cb, String host, int port) {
p("here");
    try {
      SocketChannel sc = SocketChannel.open();
      SocketAddress remote = new InetSocketAddress(InetAddress.getByName(host), port);

      sc.configureBlocking(false);
      

p("here");
      int ops = SelectionKey.OP_CONNECT;// | SelectionKey.OP_READ | SelectionKey.OP_WRITE;
p("here");
      //sc.register(this.selector, ops, cb); 
      this.registerOpsQueue.add(new R(sc, ops, cb));
      this.selector.wakeup();
p("here");
      sc.connect(remote);	
p("here");
    } catch (Throwable t) {
      cb.onError(this, t);
    }

  }

  public  void go () {
    super.go();
    for (R r : this.registerOpsQueue) {
      r.channel.register(this.selector, r.ops, r.cb);
    }

  }

  public static void main (String [] args) {
    TCPClientLoop loop = new TCPClientLoop();
    loop.start();
    Callback.TCPClientCB cb = new Callback.TCPClientCB() {
      public void onConnect(Loop l, SocketChannel ch) {
        p("onConnect: "+ch);
      }
      public void onData(Loop l, SocketChannel c, ByteBuffer b) {
        p("onData: "+b);
      }
      public void write(Loop l, ByteBuffer b) {
        p("he!");
      }
      public void onClose(Loop l, SocketChannel c) {
        p("closed: "+c);
      }
    };
    loop.createTCPClient(cb, "127.0.0.1", 8000); 
  }

  class R {
    SocketChannel channel;
    int ops;
    Callback.TCPClientCB cb;
    R (SocketChannel c, int ops, Callback.TCPClientCB cb) {
      this.channel = c;
      this.ops = ops;
      this.cb = cb;
    }
  }

}
