package event;

import java.util.Queue;
import java.util.Iterator;
import java.util.LinkedList;

import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketAddress;

public class TCPClientLoop extends TimeoutLoop {
  
  public TCPClientLoop () {
    super();
  }
  
  /**
   * May result in Callback onError being informed of the following Exceptions:
   *  java.io.IOException
   *  java.net.UnknownHostException
   *  java.nio.channels.ClosedChannelException
   */
  public SocketChannel createTCPClient (final Callback.TCPClientCB cb, String host, int port) {
    try {
      final SocketChannel sc = SocketChannel.open();
      // todo async dns
      SocketAddress remote = new InetSocketAddress(InetAddress.getByName(host), port);

      sc.configureBlocking(false);
      if (this.isLoopThread()) {
        sc.register(this.selector, SelectionKey.OP_CONNECT, new R(sc, cb));
      } else { 
        this.addTimeout(new Event.Timeout(){
          public void go(TimeoutLoop loop) {
            TCPClientLoop l = (TCPClientLoop)loop;
            try {
              sc.register(l.selector, SelectionKey.OP_CONNECT, new R(sc, cb));
            } catch (java.nio.channels.ClosedChannelException cce) {
              cb.onError(l, cce);
            } 
          }
        });
      }
      sc.connect(remote);	
      return sc;
    } catch (Throwable t) {
      cb.onError(this, t);
    }
    return null;

  }

  public void write (final SocketChannel sc, final Callback.TCPClientCB cb, final ByteBuffer buffer) {
    if (!this.isLoopThread()) {
      this.addTimeout(new Event.Timeout(){
        public void go(TimeoutLoop loop) {
          ((TCPClientLoop)loop).write(sc, cb, buffer);
        }
      });
      return;
    }
    // check in proper thread.

    SelectionKey key = sc.keyFor(this.selector);
    if (null == key) {
      cb.onError(this, sc, new RuntimeException("not a previously configured channel!"));
    } else {
    //p(sc.socket().getLocalPort()+">write:"+bin(key.interestOps()));  
      key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); 
    //p(sc.socket().getLocalPort()+"<write:"+bin(key.interestOps()));  
      R r_orig = (R)key.attachment();
      r_orig.push(buffer);
    }
  }


  public  void go () {
    assert this.isLoopThread();
    Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
    SelectionKey key;
    while (keys.hasNext()) {
		  key = keys.next();
			keys.remove();
      if (key.isValid() && key.isConnectable()){  // !isValid shouldn't happen here ...
        handleConnect(key);
      }
      if (key.isValid() && key.isReadable()) {  // nor here ...
        handleRead(key);
      }
      if (key.isValid() && key.isWritable()) {  // but read may cancel if channel is closed ... (?)
        handleWrite(key);
      }
    }

    super.go();
  }
  private final ByteBuffer buf = ByteBuffer.allocateDirect(65535);

  private void handleRead (SelectionKey key) {

    assert this.isLoopThread();
    
    SocketChannel        sc = (SocketChannel)key.channel();
    Callback.TCPClientCB cb = ((R)key.attachment()).cb;

    buf.clear();
    int i = 0;
    try {
      i = sc.read(buf);
    } catch (java.io.IOException ioe) {
      cb.onError(this, sc, ioe);
      return;
    }
    if (-1 == i){
      cb.onClose(this, sc);
    //p(sc.socket().getLocalPort()+">handleRead:"+bin(key.interestOps()));  
      key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
    //p(sc.socket().getLocalPort()+"<handleRead :"+bin(key.interestOps()));  
      //key.cancel();
    } else {	
      buf.flip();
      cb.onData(this, sc, buf);
    }
  }

  private void handleWrite(SelectionKey key) {

    assert this.isLoopThread();
    
    SocketChannel sc = (SocketChannel)key.channel();
    R             r  = (R)key.attachment();

    Queue<ByteBuffer> data = r.bufferList;
    ByteBuffer buffer = null;
    while (null != (buffer = data.peek())){
      
      try {
        sc.write(buffer);
      } catch (java.io.IOException ioe) {
        r.cb.onError(this, sc, ioe);
        //todo: received an error on write, what to do?
        //  * about unwritten data ...
        //  * about the channel 
        return;
      }
      if (buffer.remaining() != 0) {
        // couldn't write the entire buffer, jump
        // ship and wait for next time around.
        break;
      }
      data.remove();
    }

    // we've written all the data, no longer interested in writing.
    if (data.isEmpty()) {
    //p(sc.socket().getLocalPort()+">handleWrite:"+bin(key.interestOps()));  
      key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    //p(sc.socket().getLocalPort()+"<handleWrite:"+bin(key.interestOps()));  
    }
  }
  
  private void handleConnect(SelectionKey key) {

    assert this.isLoopThread();

    SocketChannel        sc = (SocketChannel)key.channel();
    Callback.TCPClientCB cb = ((R)key.attachment()).cb;
    try {
      sc.finishConnect();
    } catch (java.io.IOException ioe) {
      cb.onError(this, sc, ioe);
      return;
    }
    cb.onConnect(this, sc); 


    //p(sc.socket().getLocalPort()+">handleConnect:"+bin(key.interestOps()));  
    int io = key.interestOps();
        io |= SelectionKey.OP_READ;
        io &= ~SelectionKey.OP_CONNECT;
    key.interestOps(io);
    //p(sc.socket().getLocalPort()+"<handleConnect:"+bin(key.interestOps()));  
  }



  public void shutdownOutput (SocketChannel sc, Callback.TCPClientCB cb) {
    // TODO cancel all write operations, or check.
    try {
      sc.socket().shutdownOutput(); 
    } catch (java.io.IOException ioe) {
      cb.onError(this, sc, ioe);
    }
  }

  static String bin(int num) {
    return Integer.toBinaryString(num);
  }
  public static void main (String [] args) {
    TCPClientLoop loop = new TCPClientLoop();
    loop.start();
    Callback.TCPClientCB cb = new Callback.TCPClientCB() {
      public void onConnect(TCPClientLoop l, SocketChannel ch) {
        p("onConnect: "+ch);
        byte [] bs = "GET / HTTP/1.1\r\n\r\n".getBytes();
        l.write(ch, this, ByteBuffer.wrap(bs));
      }
      public void onData(TCPClientLoop l, SocketChannel ch, ByteBuffer b) {
        p("onData: "+b);
        //l.shutdownOutput(ch, this);
        byte [] bs = "GET / HTTP/1.1\r\n\r\n".getBytes();
        l.write(ch, this, ByteBuffer.wrap(bs));
      }
      public void onClose(TCPClientLoop l, SocketChannel ch) {
        p("closed: "+ch);
        SelectionKey key = ch.keyFor(l.selector);
        p(key);

      }
    };
    loop.createTCPClient(cb, args[0], 8000); 
  }

  class R {
    SocketChannel channel;
    Callback.TCPClientCB cb;
    ByteBuffer buffer;
    Queue<ByteBuffer> bufferList;
    R (SocketChannel c,  Callback.TCPClientCB cb) {
      this.channel = c;
      this.cb = cb;
      this.bufferList = new LinkedList<ByteBuffer>();
    }
    void push (ByteBuffer buffer) {
      this.bufferList.add(buffer);
    }
  }

}
