package event;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;

import java.io.InputStream;
import java.io.OutputStream;
/**
 *  Some rudimentary performance tests,
 *
 *  see also:
 *    ../client.node.js
 */
public class Speed {
  static final int NUM = 10000;

  static void client() {
    TCPClientLoop loop = new TCPClientLoop();
                  loop.start();

                  loop.createTCPClient( new Client(), "localhost", 4321);
  }
  
  static void classicClient() {
    try {
      Socket s = new Socket("localhost", 4321);

      OutputStream os = s.getOutputStream();
      InputStream is  = s.getInputStream();

      os.write("hello".getBytes());

      int i =0;
      while ( -1 != (i = is.read()) ) {
        os.write(i);
      } 
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  static void server() {
    TCPServerLoop loop = new TCPServerLoop();
                  loop.start();
                  loop.createTCPServer( new Server(), new InetSocketAddress(4321));


  }
static long time;
  public static void main (String [] args) {
    if (args.length < 1) {
      System.err.println("usage: [jre] event.Speed [client|server]");
    }
    
    if ("client".equals(args[0])) {
      time = System.currentTimeMillis();
      client();
    } else if ("classic-client".equals(args[0])) {
      classicClient();
    } else {
      server();
    }
  }

  static class Client extends Callback.TCPClient {
    int count;
    public void onConnect(TCPClientLoop l, SocketChannel sc) {
      l.write(sc, this, "hello".getBytes());
    }
    public void onData(TCPClientLoop l, SocketChannel sc, ByteBuffer buf) {
      l.write(sc, this, buf);
      ++count; 
      if (Speed.NUM == count) {
        p("here");
        l.shutdownOutput(sc, this);
        l.close(sc, this);
        l.st("");
        l.stopLoop();
      }
    }
    public void onClose(TCPClientLoop l, SocketChannel sc) {
      l.stopLoop();
      p((System.currentTimeMillis()-time));
    }
  } // Client

  static class Server extends Callback.TCPServer {
    public void onAccept(TCPServerLoop l, ServerSocketChannel ssc, SocketChannel sc) {
      l.createTCPClient(new Client(), sc);
    }
  }

  static void p(Object o) {
    System.out.println(o);
  }
}


