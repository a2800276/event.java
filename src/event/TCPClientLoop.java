package event;

import java.util.Queue;
import java.util.Iterator;
import java.util.LinkedList;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketAddress;

public class TCPClientLoop extends TimeoutLoop {
  
  // all data read from net goes through this buffer.
  // todo: perhaps in future allow implementation of custom
  // strategy for buffer allocation. Also determine ideal buffer size...
  private final ByteBuffer recvBuffer;

  // default size of the internal recv buffer
  public static final int DEFAULT_RECV_BUFFER_SIZE = 65536;

  public TCPClientLoop (int recvBufferSz) {
    super();

    this.dnsLoop = new DNSLoop();
    this.recvBuffer = ByteBuffer.allocateDirect(recvBufferSz);
  }
  public TCPClientLoop () {
    this(DEFAULT_RECV_BUFFER_SIZE);
  }
  
  private DNSLoop dnsLoop;  // quick and dirty hack to keep DNS Queries form blocking in
                            // out Loop. A second DNSLoop is started to queue lookups 
                            // sequentially for now. When the lookup is complete, the request 
                            // gets requeued in this loop.

  public void run() {
    this.dnsLoop.start();
    super.run();
  }
  
  public void stopLoop() {
    if (this.isLoopThread()) {
      // this is opposite to the normal semantics:
      // if we are called from within the loop thread
      // chances are we are iterating over the selected  
      // keys in  the main loop. `handleCloseAllSockets`
      // will iterate over all keys and this causes problems
      // in the backing collection of the selector
      this.addTimeout(new Callback.Timeout() {
        public void go(TimeoutLoop l) {
          TCPClientLoop.this._stopLoop();
        }
      });
      return;
    }
    _stopLoop();
  }
  private void _stopLoop() {
    TCPClientLoop.this.dnsLoop.stopLoop();
    handleCloseAllSockets();
    TCPClientLoop.super.stopLoop();
  }


  
  /**
   * May result in Callback onError being informed of the following Exceptions:
   *  java.io.IOException
   *  java.net.UnknownHostException
   *  java.nio.channels.ClosedChannelException
   */
  public void createTCPClient (final Callback.TCPClient cb, final String host, final int port) {
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

  public void createTCPClient (final Callback.TCPClient cb, final InetAddress host, final int port) {
    if (this.isLoopThread()) {
      try {
        final SocketChannel sc = SocketChannel.open();
                            sc.configureBlocking(false);

        SocketAddress remote = new InetSocketAddress(host, port);

        sc.register(this.selector, SelectionKey.OP_CONNECT, new R(sc, cb));
        sc.connect(remote);	

      } catch (Throwable t) {
        cb.onError(this, t);
      }
    } else { 
      this.addTimeout(new Callback.Timeout(){
        public void go(TimeoutLoop loop) {
          TCPClientLoop.this.createTCPClient(cb, host, port);
        }
      });
    }
  }

  /**
   * Used to create a TCPClient from a SocketChannel (typically received by a
   * server accepting connections)
   */
  public void createTCPClient (final Callback.TCPClient cb, final SocketChannel sc) {
    if (this.isLoopThread()) {
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
        sc.register(this.selector, SelectionKey.OP_READ, new R(sc, cb));


      } catch (Throwable t) {
        cb.onError(this, t);
      }
    } else {
      this.addTimeout(new Callback.Timeout() {
        public void go (TimeoutLoop l) {
          TCPClientLoop.this.createTCPClient(cb, sc);
        }
      });
    }
  }
  /**
   * Utility, avoid if possible! slowish
   *
   * The `write` methods don't make copy of the data passed to them, so as soon
   * data is passed to `write` it must remain untouch because it is place on the 
   * write queue as is.
   */
  public void write (final SocketChannel sc, final Callback.TCPClient cb, byte [] bytes) {
    write(sc, cb, ByteBuffer.wrap(bytes));
  }

  /**
   *  Used to write to a client.
   *
   *  The `write` methods don't make copy of the data passed to them, so as soon
   *  data is passed to `write` it must remain untouch because it is place on the 
   *  write queue as is.
   */
  public void write (final SocketChannel sc, final Callback.TCPClient cb, final ByteBuffer buffer) {

    if (   !sc.isConnected()      
        || !sc.isOpen()           
        || sc.socket().isClosed()
        || sc.socket().isOutputShutdown() ) 
    {
      cb.onError(this, sc, new RuntimeException("channel not open or shutdown"));
      return;
    }
    
    if (this.isLoopThread()) {
      SelectionKey key = sc.keyFor(this.selector);
      if (null == key) {
        cb.onError(this, sc, new RuntimeException("not a previously configured channel!"));
      } else {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); 
        R r = (R)key.attachment();
        if ( !(r.closePending || sc.socket().isOutputShutdown()) ) {
          r.push(buffer);
        } else {
          cb.onError(this, sc, new RuntimeException("channel no longer writable"));
        }
      }
      return;
    }

    this.addTimeout(new Callback.Timeout(){
      public void go(TimeoutLoop loop) {
        ((TCPClientLoop)loop).write(sc, cb, buffer);
      }
    });
    return;
  }
  
  public enum Shutdown {
    SHUT_RD, SHUT_WR, SHUT_RDWR
  }

  /**
   * shutdown the SocketChannel, this tries to follow posix behaviour in that
   * any data queued to be sent or received will be discarded. This definately applies
   * for the application level data queues, and _should_ also apply to data stored
   * in the OS TCP buffers.
   */
  public void shutdown (final SocketChannel sc, final Callback.TCPClient cb, Shutdown how) {
    switch (how) {
      case SHUT_WR:
        shutdownOutput(sc, cb);
        break;
      case SHUT_RD:
        shutdownInput(sc, cb);
        break;
      case SHUT_RDWR:
        shutdownOutput(sc, cb);
        shutdownInput(sc, cb);
    }
  }
  /**
   * @see shutdown
   */
  public void shutdownOutput (final SocketChannel sc, final Callback.TCPClient cb) {
    
    if (   !sc.isConnected()      
        || !sc.isOpen()           
        || sc.socket().isClosed()
        || sc.socket().isOutputShutdown()) {return;}

    if (this.isLoopThread()) {
        try { sc.socket().shutdownOutput(); } 
        catch (IOException ioe) { cb.onError(this, sc, ioe); }
        return;
    }
    
    //
    // else
    //

    this.addTimeout(new Callback.Timeout() {
      public void go (TimeoutLoop loop) {
        // TODO cancel all write operations, or check.
        shutdownOutput(sc, cb);
      }
    });
  }
  
  /**
   * @see shutdown
   */
  public void shutdownInput (final SocketChannel sc, final Callback.TCPClient cb) {
    
    if (   !sc.isConnected()      
        || !sc.isOpen()           
        || sc.socket().isClosed()
        || sc.socket().isInputShutdown()) {return;}

    if (this.isLoopThread()) {
        try { sc.socket().shutdownInput(); } 
        catch (IOException ioe) { cb.onError(this, sc, ioe); }
        return;
    }
    
    //
    // else
    //

    this.addTimeout(new Callback.Timeout() {
      public void go (TimeoutLoop loop) {
        // TODO cancel all write operations, or check.
        shutdownInput(sc, cb);
      }
    });
  }
  
  /**
   * closes the SocketChannel gracefully, attempting to send any remaining data queued
   * in the application queues and OS TCP buffers (probably), but will prevent any
   * further data to be sent from the channel.
   */
  public void close (final SocketChannel sc, final Callback.TCPClient client) {
    
    if (   !sc.isConnected()      
        || !sc.isOpen()           
        || sc.socket().isClosed()) { return; }

    if (this.isLoopThread()) {
      R r = r(sc);
      assert r != null;
      // try to (more or less) follow close/shutdown conventions:
      // `close` tries to deliver pending data (be it in app or os buffers)
      // while `shutdown` doesn't care.

      if ( sc.socket().isOutputShutdown() || !r.dataPending() ) {
        handleClose(sc); 
      } else {
        r.closePending = true;
      }
      return;
    }

    this.addTimeout(new Callback.Timeout() {
      public void go (TimeoutLoop l) {
        close(sc, client);
      }
    });
  }

  /**
   * Utility to retrieve Attachment from sc.
   * */

  R r (SocketChannel sc) {
    SelectionKey key = sc.keyFor(this.selector);
    assert key != null;
    return (R)key.attachment();
  }

  void handleClose (SocketChannel sc) {
  //  p(">>>> handleClose"+this.getClass());
    assert this.isLoopThread();

    SelectionKey key = sc.keyFor(this.selector);
    assert key != null;
    R r = (R)key.attachment();
    assert r !=  null;
    key.cancel();

    try {
      sc.close();
    } catch (IOException ioe) {
      r.cb.onError(TCPClientLoop.this, sc, ioe);
    }
    if (sc.socket().isClosed()) {
      r.cb.onClose(this, sc);
    } 

  //  p("<<<< handleClose"+this.getClass());
  }

  protected void go () {
   // p(">>>> tick"+this.getClass());
    assert this.isLoopThread();
    if (!this.isRunning()) {
      return;
    }
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
   // p("<<<< tick "+this.getClass());
  }
  

  private void handleRead (SelectionKey key) {
    //p(">>>> handleRead"+this.getClass());

    assert this.isLoopThread();
    assert key.isReadable();
    
    SocketChannel      sc = (SocketChannel)key.channel();
    Callback.TCPClient cb = ((R)key.attachment()).cb;

    this.recvBuffer.clear();
    int i = 0;
    try {
      i = sc.read(this.recvBuffer);
    } catch (IOException ioe) {
      cb.onError(this, sc, ioe);
      return;
    }
    if (-1 == i){
      key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
      cb.onEOF(this, sc); // howto: differentiated b/t which direction was closed?
    } else {	
      this.recvBuffer.flip();
      cb.onData(this, sc, this.recvBuffer);
    }
    //p("<<<< handleRead"+this.getClass());
  }

  private void handleWrite(SelectionKey key) {
    //p(">>>>>> handleWrite"+this.getClass());

    assert this.isLoopThread();
    assert key.isWritable();

    SocketChannel sc = (SocketChannel)key.channel();
    R             r  = (R)key.attachment();
    

    if (!sc.socket().isOutputShutdown()) {
      Queue<ByteBuffer> data = r.bufferList;
      ByteBuffer buffer = null;
      while (null != (buffer = data.peek())){
        try {
          int pos = buffer.position();
          int num = sc.write(buffer);
          r.cb.onWrite(this, sc, buffer, pos, num);
        } catch (IOException ioe) {

          /*
           It seems as though there is no way to determine whether the remote
           side has closed the channel. At this point though, there's nothing
           left to do but close the connection.
          */

          r.cb.onError(this, sc, ioe);

          return;
        }

        if (buffer.remaining() != 0) {

          // couldn't write the entire buffer, bail and wait for next time
          // around.
          return;
        }

        data.remove();
      }

      // we've written all the data, no longer interested in writing.
      key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

      // since there's nothing left to write, it's now safe to close in case
      // `close` was called previously.

      if (r.closePending) {
        handleClose(sc);
      }
    } else {
      r.cb.onError(this, sc, new RuntimeException("channel no longer writeable"));
    }

    //p("<<<<<< handleWrite"+this.getClass());
  }
  
  private void handleConnect(SelectionKey key) {

    assert this.isLoopThread();
    assert key.isConnectable();

    SocketChannel      sc = (SocketChannel)key.channel();
    Callback.TCPClient cb = ((R)key.attachment()).cb;

    try {
      sc.finishConnect();
      cb.onConnect(this, sc); 
    } catch (IOException ioe) {
      cb.onError(this, sc, ioe);
      return;
    }

    int io = key.interestOps();
        io |= SelectionKey.OP_READ;
        io &= ~SelectionKey.OP_CONNECT;
    key.interestOps(io);
  }
  
  /**
   * TODO: currently not graceful,
   * this is the emerency brake.
   */
  private void handleCloseAllSockets() {
    for (SelectionKey key : this.selector.keys()) {
      try {
        key.channel().close();
        key.cancel();
      } catch (java.io.IOException ioe) {
        ioe.printStackTrace();
      }
    }
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
    Callback.TCPClient cb = new Callback.TCPClient() {
      public void onConnect(TCPClientLoop l, SocketChannel ch) {
        p("onConnect: "+ch);
        byte [] bs = "GET / HTTP/1.1\r\n\r\n".getBytes();
        l.write(ch, this, ByteBuffer.wrap(bs));
      }
      public void onData(TCPClientLoop l, SocketChannel sc, ByteBuffer b) {
        p("onData: "+to_s(b));

        //l.shutdownOutput(ch, this);
        byte [] bs = "GET / HTTP/1.1\r\n\r\n".getBytes();
        l.write(sc, this, ByteBuffer.wrap(bs));
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
    Callback.TCPClient cb;
    Queue<ByteBuffer> bufferList;
    boolean closePending;

    R (SocketChannel c, Callback.TCPClient cb) {
      this.channel = c;
      this.cb = cb;
      this.bufferList = new LinkedList<ByteBuffer>();
    }
    void push (ByteBuffer buffer) {
      this.bufferList.add(buffer);
    }
    boolean dataPending() {
      return this.bufferList.size() != 0;
    }
  }
}
