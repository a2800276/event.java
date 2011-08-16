package event;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;


public class CloseSemanticsTest {
  
  static final void fail (String mes) {
    throw new RuntimeException(mes);
  }

  static boolean contains(java.util.Collection<TimeoutLoop.T> l, Callback.Timeout e) {
    for (TimeoutLoop.T t : l) {
      if (t.ev == e) {
        return true;
      }
    }
    return false;
  }

  static void p(Object o) {
    System.out.println(o);
  }
  static final int NUM2 = 1000000;
  static final int PORT = 54322;
  
  static int numWritten;
  static void client() {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {
      public void onConnect (TCPClientLoop l, SocketChannel sc) {
        p("client onConnect");
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        p("client about to write: "+bytes.length);
        client_loop.write(sc, this, bytes);
        l.close(sc, this);
        // this closes the client, the previous write will complete.
      }
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        p("client onData: wtf?");
      }
      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        //assert(pos <= numWritten);
        assert(num <= NUM2);
        numWritten += num;
        p("client onWrite: pos="+pos+" num="+num+" total ="+numWritten);
        if (numWritten == NUM2) {
          l.stopLoop();
          server_loop.stopLoop();
          p("client onWrite: final...");
          p("l :"+l.stopped);
          p("s :"+server_loop.stopped);
        }
      }
      public void onClose (TCPClientLoop l, SocketChannel c) {
        p("client onClose: "+c);
      }
      public void onError (TCPClientLoop l, SocketChannel c, Throwable ioe) {
        p("client Err");
        ioe.printStackTrace();
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }

  static int numWrittenS;
  static void clientShutdown() {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {
      public void onConnect (TCPClientLoop l, SocketChannel sc) {
        p("clientS onConnect");
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        p("clientS about to write: "+bytes.length);
        l.write(sc, this, bytes);
        // this doesn't close the client, but shuts it down after the
        // first bytes are written.
      }
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        p("clientS onData: wtf?");
      }
      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        //assert(pos <= numWritten);
        assert(num <= NUM2);
        numWrittenS += num;
        p("clientS onWrite: pos="+pos+" num="+num+" total ="+numWritten);
        if (numWrittenS == NUM2) {
          l.stopLoop();
          p("clientS onWrite: final...");
        }
        // client is shutdown, no further writes will suceed
        l.shutdown(sc, this, TCPClientLoop.Shutdown.SHUT_WR);
      }
      public void onClose (TCPClientLoop l, SocketChannel c) {
        p("clientS onClose: "+c);
      }
      public void onError (TCPClientLoop l, SocketChannel sc, Throwable ioe) {
        p("clientS err");
        ioe.printStackTrace();
        l.close(sc, this);
        l.stopLoop();
        p("clientS loop Stopped");
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }

  final static TCPServerLoop server_loop = new TCPServerLoop();
  static void server() {



    java.net.SocketAddress addr = new java.net.InetSocketAddress("127.0.0.1", PORT);              
    

    final Callback.TCPClient client = new Callback.TCPClient() {
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        p ("server data: "+b.remaining());
      }
    };

    Callback.TCPServer server   = new Callback.TCPServer() {
      public void onConnect (TCPServerLoop l, ServerSocketChannel ssc) {
        p("listening");
        client();
        clientShutdown();
      }
      public void onAccept (final TCPServerLoop l, ServerSocketChannel ssc, final SocketChannel sc) {
        p("server onAccept");
        l.createTCPClient(client, sc);

      }
    };

    server_loop.start();
    server_loop.createTCPServer(server, addr);
    p("here2");

  }
  static void usage () {
    p("usage: [jre] event.OnWriteTest [-client|-server]");
    System.exit(1);
  }
  public static void main (String [] args) {
      server();
  }

}
