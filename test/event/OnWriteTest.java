package event;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;


public class OnWriteTest {
  
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
  static final int NUM = 10;
  static final int NUM2 = 1000000;
  static final int PORT = 54321;

  static void client () {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {
      public void onConnect (TCPClientLoop l, SocketChannel c) {
        p("client onConnect");
        assert(l == client_loop);
        byte [] bytes = new byte[NUM];
        client_loop.write(c, this, bytes);
      }
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        p("client onData: wtf?");
      }
      public void onWrite (TCPClientLoop l, SocketChannel c, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        assert(pos == 0);
        assert(num == NUM);
        l.stopLoop();
        p("client onWrite: final...");
      }
      public void onError (TCPClientLoop l, SocketChannel c, java.io.IOException ioe) {
        ioe.printStackTrace();
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }
  
  static int numWritten;
  static void client2 () {
    final TCPClientLoop client_loop = new TCPClientLoop();
                        client_loop.start();
    
    Callback.TCPClient client = new Callback.TCPClient() {
      public void onConnect (TCPClientLoop l, SocketChannel c) {
        p("client2 onConnect");
        assert(l == client_loop);
        byte [] bytes = new byte[NUM2];
        p("client2 about to write: "+bytes.length);
        client_loop.write(c, this, bytes);
      }
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        assert(l == client_loop);
        p("client2 onData: wtf?");
      }
      public void onWrite (TCPClientLoop l, SocketChannel c, ByteBuffer b, int pos, int num) {
        assert(l == client_loop);
        //assert(pos <= numWritten);
        assert(num <= NUM2);
        numWritten += num;
        p("client2 onWrite: pos="+pos+" num="+num+" total ="+numWritten);
        if (numWritten == NUM2) {
          l.stopLoop();
          p("client2 onWrite: final...");
        }
      }
      public void onError (TCPClientLoop l, SocketChannel c, java.io.IOException ioe) {
        ioe.printStackTrace();
      }
    };

    client_loop.createTCPClient(client, "127.0.0.1", PORT);
  }

  static void server() {



    java.net.SocketAddress addr = new java.net.InetSocketAddress("127.0.0.1", PORT);              
    
    final TCPClientLoop clientLoop = new TCPClientLoop();
                  clientLoop.start();

    final Callback.TCPClient client = new Callback.TCPClient() {
      public void onData (TCPClientLoop l, SocketChannel c, ByteBuffer b) {
        p ("server data: "+b.remaining());
      }
    };

    Callback.TCPServer server   = new Callback.TCPServer() {
      public void onConnect (TCPServerLoop l, ServerSocketChannel ssc) {
        p("listening");
      }
      public void onAccept (final TCPServerLoop l, ServerSocketChannel ssc, final SocketChannel sc) {
        p("server onAccept");
        clientLoop.createTCPClient(client, sc);

      }
    };

    final TCPServerLoop server_loop = new TCPServerLoop();
    server_loop.start();
    server_loop.createTCPServer(server, addr);
    p("here2");

  }
  static void usage () {
    p("usage: [jre] event.OnWriteTest [-client|-server]");
    System.exit(1);
  }
  public static void main (String [] args) {
    
    if (args.length > 0 && "-server".equals(args[0])) {
      server();
    } else {
      client();
      client2();
    }
  }

}
