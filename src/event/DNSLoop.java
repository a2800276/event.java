package event;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 * This is a utility to provide non-blocking DNS lookups in a stupidly
 * simplistic fashion. I.e. this is a single thread resolving all DNS
 * queries via blocking java `getHostByName()` means.
 *
 */

public class DNSLoop extends TimeoutLoop {

  DNSLoop() {
    super();
  }

  void lookup (final String host, final CB callback) {
    addTimeout(new Event.Timeout() {
      public void go (TimeoutLoop loop) {
        try {
          InetAddress addr = InetAddress.getByName(host);
          callback.addr(addr, null);
        } catch (UnknownHostException uhe) {
          callback.addr(null, uhe); 
        }
      }
    });
  }

  static interface CB {
    void addr(InetAddress addr, UnknownHostException t);
  }

  static void p(Object o) {
    System.out.println(o);
  }

}

