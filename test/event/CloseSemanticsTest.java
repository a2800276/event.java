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

        assert(l == client_loop);

        client_loop.write(sc, this, new byte[NUM2]);
        
        // this closes the client, the previous write will complete.
        l.close(sc, this);
      }

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {

        fail ("client onData should not have been called");

      }

      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {

        assert(l.isLoopThread());
        assert(l == client_loop);
        assert(num <= NUM2);

        numWritten += num;

        if (numWritten == NUM2) {
          pass("'client' wrote: "+numWritten);
        }
      }
      public void onClose (TCPClientLoop l, SocketChannel c) {
        pass ("'client' onClose: "+c);
        l.stopLoop();
        clienteof();
      }
      public void onError (TCPClientLoop l, SocketChannel c, Throwable ioe) {
        fail("client Err");
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

        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        bytes[0] = 0x01;

        client_loop.write(sc, this, bytes);
      }

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        fail("'clientEOF' received data");
      }

      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(num <= NUM2);
      }

      public void onClose (TCPClientLoop l, SocketChannel c) {
        pass ("'clientEOF' onClose: "+c);
        l.stopLoop();
        client_server_closes();
      }

      public void onEOF (TCPClientLoop l, SocketChannel sc) {
        pass("'clientEOF' onEOF");
        l.close(sc, this);

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
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        bytes[0] = 0x02;

        numWritten = 0;
        client_loop.write(sc, this, bytes);
      }

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        fail("client_s_close received data");
      }

      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(num <= NUM2);
        numWritten += num;
      }

      public void onClose (TCPClientLoop l, SocketChannel c) {
        pass("'server close' onClose");
        l.stopLoop();
        clientShutdown();
      }

      public void onEOF (TCPClientLoop l, SocketChannel sc) {
        pass("'server close' onEOF");
      }

      int errNo = 0;
      public void check (String mes) {
        switch (errNo) {
          case 0:
            if ("Connection reset by peer".equals(mes) || "Broken pipe".equals(mes)) {
              pass("'server close' : "+errNo+" : "+mes);
            } else {
              fail ("'server close' : "+errNo+" : "+mes);
            }
            break;
          case 1:
          case 2:
            if ("Socket is not connected".equals(mes)){
              pass("'server close' : "+errNo+" : "+mes);
            } else {
              fail ("'server close' : "+errNo+" : "+mes);
            }
            break;
          default:
            fail ("'server close' : "+errNo+" : "+mes);
        }
        errNo++;
      }

      public void onError (TCPClientLoop l, SocketChannel sc, Throwable ioe) {
        // behaviour is weird if the server closes socket unexpectantly
        // read will fail and not return -1 (Connection reset)
        // write will fail (broken pipe)
        //
        // socket.closed, connected, shutdown won't indicate the proper state.

        check(ioe.getMessage());

        l.shutdown(sc, this, TCPClientLoop.Shutdown.SHUT_RDWR);
        l.close(sc, this);
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }
  static void clientShutdown() {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {
      public void onConnect (TCPClientLoop l, SocketChannel sc) {
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        l.write(sc, this, bytes);
        // this doesn't close the client, but shuts it down after the
        // first bytes are written.
      }
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        p("clientS onData: wtf?");
      }
      boolean subsequent;
      public void onWrite (TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(num <= NUM2);
        
        if (subsequent) {
          fail ("'client shutdown' subsequent bytes suceeded");
        }
        subsequent = true;
        // client is shutdown, no further writes will suceed
        l.shutdown(sc, this, TCPClientLoop.Shutdown.SHUT_WR);
      }
      public void onClose (TCPClientLoop l, SocketChannel c) {
        pass("'client shutdown' : onClose");

        l.stopLoop();
        server_loop.stopLoop();
      }
      public void onError (TCPClientLoop l, SocketChannel sc, Throwable ioe) {

        if ("channel no longer writeable".equals(ioe.getMessage()) ) {
          pass("'client shutdown' : error on write");
        } else {
          fail("'client shutdown' : unexpected err :"+ioe.getMessage());
          ioe.printStackTrace();
        }
        l.close(sc, this);
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }

  final static TCPServerLoop server_loop = new TCPServerLoop();
  static void server() {

    java.net.SocketAddress addr = new java.net.InetSocketAddress("127.0.0.1", PORT);              

    final Callback.TCPClient client = new Callback.TCPClient() {

      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
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
        client();
      }

      public void onAccept (final TCPServerLoop l, ServerSocketChannel ssc, final SocketChannel sc) {
        l.createTCPClient(client, sc);
      }

    };

    server_loop.start();
    server_loop.createTCPServer(server, addr);

  }
  
  public static void main (String [] args) {
      server();
  }
  
  static final void pass (String mes) {
    p("passed: "+mes);
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
