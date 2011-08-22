package event.util;

import java.util.Queue;
import java.util.LinkedList;

public class ByteArrayPool {
  int maxFree = 10;
  int size;
  Queue<byte[]> q;

  public ByteArrayPool(int size) {
    this.q = new LinkedList<byte[]>();
    this.size = size;
  }
  public byte[] get() {
    byte [] bytes = this.q.poll();
    return bytes == null ? new byte[size] : bytes;
  }

  public void putBack(byte[]bytes) {
    assert null != bytes;
    assert bytes.length == this.size;
    
    if (this.q.size() < maxFree) {
      this.q.add(bytes);
    }
  }
}
