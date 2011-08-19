
package event;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;

import static event.Test.p;
import static event.Test.dump;


public class EchoTest extends Test {

  long start;

  public EchoTest() {
    super();
    setClientServer(new EchoTest.Client(), new EchoTest.Server());
  }

  static ByteBuffer copyBuffer (ByteBuffer orig) {
    ByteBuffer ret = ByteBuffer.allocate(orig.remaining());

    orig.mark();
    ret.put(orig);
    ret.flip();
    orig.reset();

    return ret;
  }
  
  class Server extends Test.Server {
    public void onAccept(TCPServerLoop l, ServerSocketChannel ssc, SocketChannel sc){
      l.createTCPClient(new EchoTest.EchoClient(), sc);
    }
  }
  class EchoClient extends Test.Client {
    Random rnd = new Random(0L);
    public void onData(TCPClientLoop l, SocketChannel sc, ByteBuffer buf) {
       int    len = buf.remaining();
      byte [] arr = new byte[len];
      byte [] chk = new byte[len];
     p("s read: "+len); 
      buf.mark();
      buf.get(arr);
      buf.reset();
      rnd.nextBytes(chk);

      if (!cmp(arr, chk)) {
        p("server failure!");
        dump(arr,chk);
        System.exit(1);
      }

      l.write(sc, this, copyBuffer(buf));
    }
    public void onWrite(TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
      p("s echoed: "+num +" from: "+b.hashCode()+" pos="+pos);
    }
  
  }
  class Client extends Test.Client {
    Random back  = new Random(0L); 
    Random forth = new Random(0L); 
        
    final int TOT = 4000000;
        int count = TOT;
        int NUM   =  100000;
    byte [] bytes = new byte[NUM];
      int numRead = 0;

    public void onConnect(TCPClientLoop l, java.nio.channels.SocketChannel sc) {
      EchoTest.this.start = System.currentTimeMillis();
      back.nextBytes(bytes);
      l.write(sc, this, bytes);
      count -= NUM;
    }
  

    public void onWrite(TCPClientLoop l, SocketChannel sc, ByteBuffer b, int pos, int num) {
      p("c wrote: "+num);
    }
    public void onData(TCPClientLoop l,  SocketChannel sc, ByteBuffer buf) {
      int len = buf.remaining();
      p("c read:"+len); 
      check(buf);
      if (0 >= count) {
        // EchoTest.this.done();
      } else {
        back.nextBytes(bytes);
        l.write(sc, this, bytes);
        count -= NUM;
      }
      numRead += len;
      if (numRead == TOT) {
        EchoTest.this.done();
        p(TOT + "time: "+(System.currentTimeMillis()-start));
      }
    }
    
    
    void check(ByteBuffer buf) {
       int    len = buf.remaining();
      byte [] arr = new byte[len];
      byte [] chk = new byte[len];

      buf.get(arr);
      forth.nextBytes(chk);
     
      //boolean passed = java.util.Arrays.equals(arr, chk);
      boolean passed = cmp(arr, chk);

      if (!passed) {
        p("-----");     
        dump(arr, chk);
        fail ("arrays not equal!");
      }

    }
  }
  public static void main (String [] args) throws Throwable {
    if (args.length == 1 && args[0].equals("-minitest")) {
      
      new EchoTest().minitest();
      System.exit(0);
    }

    EchoTest test = new EchoTest();
             test.runTest();
  }
  void minitest () throws Throwable{
    Random r1 = new Random(0L);
    Random r2 = new Random(0L);
   
    int NUM_CMP = 8000000;
    int NUM_PCS = 65535;

    byte [] orig = new byte[NUM_CMP];
    r1.nextBytes(orig);
    byte [] cmp  = new byte[NUM_PCS];
    r2.nextBytes(cmp);
    int count = 0;

    for (int i =0; i!= NUM_CMP; ++i) {
      if (NUM_PCS == count) {
        count = 0;
        r2.nextBytes(cmp);
      }
      if (0 == (i % 10000))
        p(i + " : o= " + orig[i] + " c= : "+ cmp[count]);
      if (orig[i] != cmp[count]) {
        p(i + " : o= " + orig[i] + " c= : "+ cmp[count]);
        return;
      }
      count++;
    }
  }

  static boolean cmp (byte [] b1, byte [] b2) {
    if (b1.length != b2.length) {
      p("len unequal");
      return false;
    }
    for (int i = 0; i!= b1.length; ++i) {
      if (b1[i] != b2[i]) {
        p("!= at "+i);
        return false;
      }
    }
    return true;
  }

  class Random extends java.util.Random {
    public Random (long seed) { super(seed); }
    /**
     * default always grabs 4 bytes of stream, so in
     * case the generated arrays aren't always divisible by
     * 4, the stream may not be reproducible
     */
    public void nextBytes(byte [] bytes) {
      for (int i = 0; i!= bytes.length; ++i) {
        bytes[i] = (byte)(nextInt());
      }
    }
  
  }
}
