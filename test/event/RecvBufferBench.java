
package event;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;

import java.util.LinkedList;

import static event.Test.p;
import static event.Test.dump;


public class RecvBufferBench extends Test {

  long start;

  public RecvBufferBench() {
    super();
    setClientServer(new RecvBufferBench.Client(), new Test.AcceptServer());
  }

  class Client extends Test.Client {
        
    final long TOT   = 40000000;
          long count = TOT;
          int NUM   =  100000;

    //byte [] bytes = new byte[NUM];
    ByteBuffer bytes = ByteBuffer.allocate(NUM);
      long numWritten = 0;
      long numSent    = 0;

    public void onConnect(TCPClientLoop l, java.nio.channels.SocketChannel sc) {
      RecvBufferBench.this.start = System.currentTimeMillis();

      l.write(sc, this, bytes);
      numSent += NUM;
    }


    public void onWrite(TCPClientLoop l, SocketChannel sc, ByteBuffer buf, int pos, int num) {
      

      numWritten += num;
      if (numWritten == TOT && num != 0) {
        //p(TOT + " time: "+(System.currentTimeMillis()-start));
        p((System.currentTimeMillis()-start));
        l.addTimeout(20, new Callback.Timeout() {
          // this is a bit of a cludge so as not to have to write a server.
          // give the server a bit of time to settle and don't roughly
          // kill it right away...
          public void go (event.TimeoutLoop l) {
            RecvBufferBench.this.done();
            RecvBufferBench.next();
          }
        });
        return;
      }
//      if (0 == (numWritten % 100000)) {
//        p(numWritten);
//      }
      if (numSent < TOT) {
        if (buf.remaining() == 0) {
          buf.flip();
          l.write(sc, this, buf);
          numSent+=NUM;
        }
      }
    }
    
    
  }
  static RecvBufferBench test; 
  static void next() {
    if (null != test) {
      try {
        test.serverL.join();
      } catch (Throwable t) {
        t.printStackTrace();
        System.exit(1);
      }
    }
    if (0 == tests.size()) {
      p("done.");
      return;
    }
    int i = tests.pollLast();

    System.out.print(i + ":");

    test         = new RecvBufferBench();
    test.serverL = new TCPServerLoop(i);
    test.runTest();



  }

  static LinkedList<Integer> tests = new LinkedList<Integer>();
  public static void main (String [] args) throws Throwable {

    int sz = TCPClientLoop.DEFAULT_RECV_BUFFER_SIZE;
    p(sz);
    // somewhere between 65636 and 131072 seems ideal.
    for (int i=65636; i<131072; i+=4096) {
      tests.push(i);
    }
    next();
  }
}
