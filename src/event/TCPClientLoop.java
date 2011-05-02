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
    this.dnsLoop = new DNSLoop();
  }
  
  private DNSLoop dnsLoop;

  public void run() {
    this.dnsLoop.start();
    super.run();
  }
  
  public void stopLoop() {
    this.dnsLoop.stopLoop();
    super.stopLoop();
  }

  
  /**
   * May result in Callback onError being informed of the following Exceptions:
   *  java.io.IOException
   *  java.net.UnknownHostException
   *  java.nio.channels.ClosedChannelException
   */
  public void createTCPClient (final Callback.TCPClientCB cb, final String host, final int port) {
    this.dnsLoop.lookup(host, new DNSLoop.CB() {
      public void addr (InetAddress addr, java.net.UnknownHostException une) {
        if (null != une) {
          cb.onError(TCPClientLoop.this, une);
        } else {
          TCPClientLoop.this.createTCPClient(cb, addr, port);
        }
      }   
    });
  }   
  public void createTCPClient (final Callback.TCPClientCB cb, final InetAddress host, final int port) {
    try {
      final SocketChannel sc = SocketChannel.open();
                          sc.configureBlocking(false);
      
      //
      // todo async dns
      //    either:
      // by calling `getByName` in a seperate thread and injecting
      // the result, or by using an async library. (http://www.xbill.org/dnsjava/)
      //

      SocketAddress remote = new InetSocketAddress(host, port);

      if (this.isLoopThread()) {
        sc.register(this.selector, SelectionKey.OP_CONNECT, new R(sc, cb));
      } else { 
        this.addTimeout(new Callback.Timeout(){
          public void go(TimeoutLoop loop) {
            TCPClientLoop l = (TCPClientLoop)loop;
                          l.createTCPClient(cb, host, port);
          }
        });
      }
      sc.connect(remote);	
    } catch (Throwable t) {
      cb.onError(this, t);
    }
  }

  /**
   * Used to create a TCPClient from a SocketChannel (typically received by a
   * server accepting connections)
   */
  public void createTCPClient (final Callback.TCPClientCB cb, final SocketChannel sc) {
    try {
      if (null == sc) {
        cb.onError(this, "channel is null");
        return;
      }
      if (!sc.isConnected()) {
        cb.onError(this, "channel not connected!");
        return;
      }
      if (sc.isBlocking()) {
        sc.configureBlocking(false);
        if (sc.isBlocking()) {
          cb.onError(this, "can't make channel non-blocking");
          return;
        } 
      } 
      if (this.isLoopThread()) {
        sc.register(this.selector, SelectionKey.OP_READ, new R(sc, cb));
      } else {
        this.addTimeout(new Callback.Timeout() {
          public void go (TimeoutLoop l) {
            TCPClientLoop loop = (TCPClientLoop) l;
                          loop.createTCPClient(cb, sc);
          }
        });
      }

    } catch (Throwable t) {
      cb.onError(this, t);
    }
  }
  /**
   * Utility, avoid if possible! slowish
   */
  public void write (final SocketChannel sc, final Callback.TCPClientCB cb, byte [] bytes) {
    write(sc, cb, ByteBuffer.wrap(bytes));
  }

  /**
   *  Used to write to a client.
   */
  public void write (final SocketChannel sc, final Callback.TCPClientCB cb, final ByteBuffer buffer) {

    // check in proper thread.
    if (!this.isLoopThread()) {
      this.addTimeout(new Callback.Timeout(){
        public void go(TimeoutLoop loop) {
          ((TCPClientLoop)loop).write(sc, cb, buffer);
        }
      });
      return;
    }

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

  public void shutdownOutput (final SocketChannel sc, final Callback.TCPClientCB cb) {

    if (this.isLoopThread()) {
        try {
          sc.socket().shutdownOutput(); 
        } catch (java.io.IOException ioe) {
          cb.onError(this, sc, ioe);
        }
        return;
    }
    
    //
    // else
    //

    this.addTimeout(new Callback.Timeout() {
      public void go (TimeoutLoop loop) {
        // TODO cancel all write operations, or check.
        ((TCPClientLoop)loop).shutdownOutput(sc, cb);
      }
    });
  }

  public void close (final SocketChannel sc, final Callback.TCPClientCB client) {
    // can't close channel immediately, because stuff may still need to be written...
    // ...but... if the channel is not writable, not everything will be written ...
    this.addTimeout(new Callback.Timeout() {
      public void go (TimeoutLoop l) {
        try {
          sc.close();
        } catch (Throwable t) {
          client.onError(TCPClientLoop.this, sc, t);
        }
      }
    });
  }

  protected void go () {
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
    assert key.isReadable();
    
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
    assert key.isWritable();

    SocketChannel sc = (SocketChannel)key.channel();
    R             r  = (R)key.attachment();

    Queue<ByteBuffer> data = r.bufferList;
    ByteBuffer buffer = null;
    while (null != (buffer = data.peek())){
      
      try {
        long num = sc.write(buffer);
        //p("wrote: "+num);
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
    assert key.isAcceptable();

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




  static String bin(int num) {
    return Integer.toBinaryString(num);
  }
  static String to_s (ByteBuffer buf) {
    StringBuilder b = new StringBuilder();
    while (buf.position() != buf.limit()) {
      b.append((char)buf.get());
    }
    return b.toString();
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
      public void onData(TCPClientLoop l, SocketChannel sc, ByteBuffer b) {
        p("onData: "+to_s(b));

        //l.shutdownOutput(ch, this);
        byte [] bs = "GET / HTTP/1.1\r\n\r\n".getBytes();
        //l.write(ch, this, ByteBuffer.wrap(bs));
        l.close(sc, this);
        l.stopLoop();
      }
      public void onClose(TCPClientLoop l, SocketChannel ch) {
        p("closed: "+ch);
        SelectionKey key = ch.keyFor(l.selector);
        p(key);

      }
    };
    loop.createTCPClient(cb, args[0], 80); 
  }

  class R {
    SocketChannel channel;
    Callback.TCPClientCB cb;
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
