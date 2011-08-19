package event;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;


/**
 * Generic Impl to facilitate Tests
 */

public class Test {

  public static void p (Object o) {
    System.out.println(o);
  }
  public static void p(byte [] os) {
    int i = 0;
    for (Object o : os) {
      p(i + ": "+ o);
      i++;
    }
  }

  public static void dump (byte [] o, byte [] o2) {
    if (o.length != o2.length) {
      p("unequal len");
    } 
    for (int i = 0; i!= o.length; ++i) {
      p(i + "["+o[i]+"] ["+o2[i]+"]");
    }
    p("=============");
  }

  final TCPServerLoop serverL = new TCPServerLoop();
  final TCPClientLoop clientL = new TCPClientLoop();

  Callback.TCPClient client;
  Callback.TCPServer server;

  final    int port = 54321;
  final String host = "127.0.0.1";
  
  public Test (){}
  public Test (Test.Client client, Test.Server server) {
    this.client = client;
    this.server = server;
  }

  public void setClientServer(Test.Client client, Test.Server server) {
    this.client = client;
    this.server = server;
  }

  public void runTest () {
    
    serverL.start();
    clientL.start();
    serverL.createTCPServer(this.server, new java.net.InetSocketAddress(host, port));
  }

  public void fail (String mes) {
    p(mes);
    done();
  }
  public void done () {
    serverL.stopLoop();
    clientL.stopLoop();
  }

  class Client extends Callback.TCPClient {
    public void onData(TCPClientLoop l, SocketChannel sc, ByteBuffer buf) {
    }
  }

  static ByteBuffer copyBuffer (ByteBuffer orig) {
    ByteBuffer ret = ByteBuffer.allocate(orig.remaining());

    orig.mark();
    ret.put(orig);
    ret.flip();
    orig.reset();

    return ret;
  }

  class EchoClient extends Client {
    public void onData(TCPClientLoop l, SocketChannel sc, ByteBuffer buf) {
      l.write(sc, this, copyBuffer(buf));
    }
  }
  

  class Server extends Callback.TCPServer {

    public void onConnect(TCPServerLoop l, ServerSocketChannel ssc) {
      // start the client side of the test.
      Test.this.clientL.createTCPClient(Test.this.client, Test.this.host, Test.this.port);
    } 
    public void onAccept(TCPServerLoop l, ServerSocketChannel ssc, SocketChannel sc){}
  }
  /**
   * Dummy Server accepts the connection, receives and discards any
   * data sent.
   */

  class AcceptServer extends Server {
    public void onAccept(TCPServerLoop l, ServerSocketChannel ssc, SocketChannel sc){
      l.createTCPClient(new Test.Client(), sc);
    } 
  }

  /**
   * Echo Server sends everything back to the origin
   */
  class EchoServer extends Server {
    public void onAccept(TCPServerLoop l, ServerSocketChannel ssc, SocketChannel sc){
      l.createTCPClient(new Test.EchoClient(), sc);
    }
  }




}
