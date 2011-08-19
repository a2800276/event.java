
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

    byte [] bytes = new byte[NUM];
      int numWritten = 0;

    public void onConnect(TCPClientLoop l, java.nio.channels.SocketChannel sc) {
      RecvBufferBench.this.start = System.currentTimeMillis();
      l.write(sc, this, bytes);
    }


    public void onWrite(TCPClientLoop l, SocketChannel sc, ByteBuffer buf, int pos, int num) {
      numWritten += num;
      if (numWritten == TOT) {
        RecvBufferBench.this.done();
        p(TOT + "time: "+(System.currentTimeMillis()-start));
        RecvBufferBench.this.next();
      }
//      if (0 == (numWritten % 100000)) {
//        p(numWritten);
//      }
      l.write(sc, this, bytes);
    }
    
    
  }
  static RecvBufferBench test; 
  static void next() {
    if (null != test) {
      try {
        test.serverL.join();
        p(test.serverL.getState());
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

    p("Testing: "+i);
   
    test         = new RecvBufferBench();
    test.serverL = new TCPServerLoop(i);
    test.runTest();



  }

  static LinkedList<Integer> tests = new LinkedList<Integer>();
  public static void main (String [] args) throws Throwable {

    int sz = TCPClientLoop.DEFAULT_RECV_BUFFER_SIZE / 2;
    p(sz);
    for (int i=1; i!=5; ++i) {
      tests.push(i*sz);
    }
    next();
  }
}
