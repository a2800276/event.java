package event;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;


public class CloseSemanticsTest {
  



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
        
        // this closes the client, the previous write will complete.
        l.close(sc, this);
      }

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        fail ("client onData should not have been called");
      }

      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(num <= NUM2);
        numWritten += num;
        p("client onWrite: pos="+pos+" num="+num+" total ="+numWritten);
        if (numWritten == NUM2) {
          l.stopLoop();
          p("client onWrite: final...");
          clienteof();
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

  static void clienteof() {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {

      public void onConnect (TCPClientLoop l, SocketChannel sc) {
        p("clientEOF onConnect");
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        bytes[0] = 0x01;

        p("clientEOF about to write: "+bytes.length);
        client_loop.write(sc, this, bytes);
      }

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        fail("clientEOF received data");
      }

      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(num <= NUM2);
        p("clientEOF onWrite: pos="+pos+" num="+num+" total ="+numWritten);
      }

      public void onClose (TCPClientLoop l, SocketChannel c) {
        p("clientEOF onClose: "+c);
      }

      public void onEOF (TCPClientLoop l, SocketChannel c) {
        p("clientEOF onEOF");
        l.stopLoop();

        client_server_closes();
      }

      public void onError (TCPClientLoop l, SocketChannel c, Throwable ioe) {
        p("clientEOF Err");
        ioe.printStackTrace();
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }

  static void client_server_closes() {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {

      public void onConnect (TCPClientLoop l, SocketChannel sc) {
        p("client_s_close onConnect");
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        bytes[0] = 0x02;

        p("client_s_close about to write: "+bytes.length);
        client_loop.write(sc, this, bytes);
      }

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        fail("client_s_close received data");
      }

      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(num <= NUM2);
        p("client_s_close onWrite: pos="+pos+" num="+num+" total ="+numWritten);
      }

      public void onClose (TCPClientLoop l, SocketChannel c) {
        p("client_s_close onClose: "+c);
        l.stopLoop();
        server_loop.stopLoop();
      }

      public void onEOF (TCPClientLoop l, SocketChannel sc) {
        p("client_s_close onEOF >>>>>");
        l.close(sc, this);
      p("isClosed : "+sc.socket().isClosed());
      p("isConnected : "+sc.socket().isConnected());
      p("isInputShutdown : "+sc.socket().isInputShutdown());
      p("isOutputShutdown : "+sc.socket().isOutputShutdown());
      p("sc.isConnected : "+sc.isConnected());
      p("isOpen : "+sc.isOpen());
        p("client_s_close onEOF <<<<<");

        //clientShutdown();
      }

      public void onError (TCPClientLoop l, SocketChannel sc, Throwable ioe) {
        p("client_s_close Err >>>>");
      p("isClosed : "+sc.socket().isClosed());
      p("isConnected : "+sc.socket().isConnected());
      p("isInputShutdown : "+sc.socket().isInputShutdown());
      p("isOutputShutdown : "+sc.socket().isOutputShutdown());
      p("sc.isConnected : "+sc.isConnected());
      p("isOpen : "+sc.isOpen());
        p(ioe.getMessage());
        ioe.printStackTrace(System.out);
        l.shutdown(sc, this, TCPClientLoop.Shutdown.SHUT_RDWR);
        l.close(sc, this);
        p("client_s_close Err <<<<");
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
        server_loop.stopLoop();
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
        if (0x01 == b.get(0)) {
          l.shutdownOutput(c, this);
        }
        if (0x02 == b.get(0)) {
          l.close(c, this);
        }
      }

    };

    Callback.TCPServer server   = new Callback.TCPServer() {

      public void onConnect (TCPServerLoop l, ServerSocketChannel ssc) {
        p("listening");
        client_server_closes();
      }

      public void onAccept (final TCPServerLoop l, ServerSocketChannel ssc, final SocketChannel sc) {
        p("server onAccept");
        l.createTCPClient(client, sc);
      }

    };

    server_loop.start();
    server_loop.createTCPServer(server, addr);

  }
  
  public static void main (String [] args) {
      server();
  }

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
}
