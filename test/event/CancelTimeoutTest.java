package event;


public class CancelTimeoutTest {
  
  static final void fail (String mes) {
    throw new RuntimeException(mes);
  }
  static void testTimeoutCancel() {
    final TimeoutLoop loop = new TimeoutLoop();
                      loop.start();

    final Event.Timeout toCancel = new Event.Timeout(1000) {
      public void go(TimeoutLoop l) {
        fail("should have been canceled");
      } 
    };

    long tid  = loop.addTimeout(toCancel);

    long tid2 = loop.addTimeout(new Event.Timeout() {
      public void go(TimeoutLoop l) {
        if (loop != l) {
          fail ("not the correct loop!");
        }

        if (!contains(loop.timeouts, toCancel)) {
          fail ("event not in loop");
        }
      }
    });

    loop.cancelTimeout(tid);
    
    tid2 = loop.addTimeout(new Event.Timeout() {
      public void go(TimeoutLoop l) {
        if (loop != l) {
          fail ("not the correct loop 2!");
        }

        if (contains(loop.timeouts, toCancel)) {
          fail ("event still in loop");
        }
        l.stopLoop();     
      }
    });
  }

  static boolean contains(java.util.Collection<TimeoutLoop.T> l, Event.Timeout e) {
    for (TimeoutLoop.T t : l) {
      if (t.ev == e) {
        return true;
      }
    }
    return false;
  }
  static void testIntervalCancel() {
    final TimeoutLoop loop = new TimeoutLoop();
                      loop.start();

    final Event.Timeout toCancel = new Event.Timeout(1000) {
      public void go(TimeoutLoop l) {
        fail("should have been canceled");
      } 
    };

    long tid  = loop.addInterval(toCancel);


    loop.cancelTimeout(tid);
    
    loop.addTimeout(new Event.Timeout() {
      public void go(TimeoutLoop l) {
        assert l == loop;
        if (contains(loop.timeouts, toCancel)) {
          fail ("event still in loop");
        }
        l.stopLoop();     
      }
    });
  }
  
  static void testCancelinLoop() {
    final TimeoutLoop loop = new TimeoutLoop();
                      loop.start();

    final Event.Timeout toCancel = new Event.Timeout(1000) {
      public void go(TimeoutLoop l) {
        fail("should have been canceled");
      } 
    };

    loop.addTimeout(new Event.Timeout(){
      public void go (TimeoutLoop l) {
        long tid = l.addTimeout(toCancel);
        loop.cancelTimeout(tid);
      }
    });


    
    loop.addTimeout(new Event.Timeout() {
      public void go(TimeoutLoop l) {
        assert l == loop;
        if (contains(loop.timeouts, toCancel)) {
          fail ("event still in loop");
        }
        if (contains(loop.newTimeouts, toCancel)) {
          fail("in nt");
        }
        l.stopLoop();     
      }
    });
  }

  static void p(Object o) {
    System.out.println(o);
  }

  public static void main (String [] args) {
    //testTimeoutCancel();
    //testIntervalCancel();
    testCancelinLoop();
  }

}
