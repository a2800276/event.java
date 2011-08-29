package event.util;

import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import java.nio.ByteBuffer;

public class BufferPool {
  int maxFree = 50;
  int numAllocated;  
  int numDestroyed;  
  static Random rnd = new Random();

  private PriorityQueue<ByteBuffer> q;
  class ByteBufferCapComp implements Comparator<ByteBuffer> {
    public int compare(ByteBuffer b1, ByteBuffer b2) {
      if (b1.capacity() ==  b2.capacity()) {
        return 0;
      }
      if (b1.capacity() < b2.capacity()) {
        return -1;
      }
      return 1;
    }
  }

  public BufferPool () {
    this.q = new PriorityQueue<ByteBuffer>(maxFree, new ByteBufferCapComp());  
  }
  
  public ByteBuffer get(int minCapacity) {
    ByteBuffer ret = null;

    for (Iterator<ByteBuffer> it = this.q.iterator(); it.hasNext();) {
      ByteBuffer buf = it.next();
      if (buf.capacity() >= minCapacity) {
        ret = buf;
        it.remove();
        break;
      }
    }
    if (ret == null) {
      p("allocated: "+ (numAllocated++));
    }
    // TODO: remove this, was a debug helper to posiively identify buffers, because
    // `equals` and `hashCode` is thoroughly broken.
    ret = (ret == null) ? ByteBuffer.allocate(minCapacity + (Math.abs(rnd.nextInt())%100) ) : (ByteBuffer)ret.clear();
    return ret;
  }

  public void putBack(ByteBuffer buf) {
    assert buf.remaining() == 0;
    this.q.add(buf);
    if (this.q.size() > this.maxFree) {
      this.q.poll();
      p("destroyed: "+(numDestroyed++)+":"+this.q.size());
    } else {
      p("stat : "+this.q.size()+":"+this.maxFree);
    }
  }

  static void p(Object m) {
    System.out.println(m);
  }
  public static void main (String [] args) {
    ByteBuffer [] b = new ByteBuffer[1];
    b[0] = ByteBuffer.allocate(100);
    p(b);
  }
}
