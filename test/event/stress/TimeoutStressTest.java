package event.stress;

import event.Callback;
import event.TimeoutLoop;

import java.util.Queue;
import java.util.LinkedList;

public class TimeoutStressTest  {
  
  Queue<Worker> queue = new LinkedList<Worker>();
  TimeoutLoop   loop  = new TimeoutLoop();

  int max;
  int numCreated;
  int numIterations;
  

  int numHit;  
  int numMiss;

  public TimeoutStressTest (int max, int iterations) {
    this.max = max;
    this.numIterations = iterations;
  }

  void begin () {
    long start = System.nanoTime();
    this.loop.start();

    for (int stop = numIterations ; stop !=0 ; --stop) {
      stressTest();
    }
    synchronized (this) {
      try { this.wait(); } catch (Throwable t) {t.printStackTrace(); System.exit(1);}
    }
    p("hits      " + numHit);
    p("mrs:      " + numMiss);
    p("in:(ms)   " + (System.nanoTime()-start) / 1000000);

    p("expected: " + (numHit * 5)/max );  
  }

  void stressTest () {
    Callback.Timeout t1 = new Callback.Timeout () {
      public void go (TimeoutLoop loop) {
        Worker w = getWorker();
        if (null == w) {
          // try again
          loop.addTimeout(workerCount%5, this);
          ++numMiss;
          return;
        }
        ++numHit;
        loop.addTimeout(workerCount%5, w);
      }
    };

    if (this.loop.isLoopThread()) {
      t1.go(this.loop);
    } else {
      this.loop.addTimeout(workerCount%5, t1);
    }


  }

  Worker getWorker() {

    Worker w  = this.queue.poll();
    if (null != w) {
      return w;
    }

    if (this.numCreated < this.max) {
      w = new Worker();
      this.numCreated++;
      return w;
    }

    return null;
  }
  void returnWorker(Worker w) {
    this.queue.add (w);
  }
  
  static int workerCount;
  class Worker extends Callback.Timeout {
    int id;
    Worker() {
      //super( (long)(workerCount %5) ); 
      super();
      this.id = workerCount++;
    }

    public void go (TimeoutLoop loop) {
      returnWorker(this); 
      numIterations--;
//      if (0 == (numIterations % 100)) {
//        dump(numIterations);
//      }
      if (0 == numIterations) {
        synchronized(TimeoutStressTest.this) {
          TimeoutStressTest.this.notify();
          TimeoutStressTest.this.loop.stopLoop();
        }
      } 
    };

    void dump(int i) {
      p(id + ":" + i);
    }
  }

  static void p (Object o) {
    System.out.println(o);
  }

  public static void main (String [] args) {
    TimeoutStressTest test = new TimeoutStressTest(10, 10000);
                      test.begin();
                      
                      test = new TimeoutStressTest(100, 10000);
                      test.begin();
  }

}
