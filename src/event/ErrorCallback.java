package event;

public interface ErrorCallback {

  public void onError (Throwable t);
  static class DefaultErrorCallback implements ErrorCallback {
    private DefaultErrorCallback(){}
    private static ErrorCallback errCB;
    public static ErrorCallback getDefault() {
      if (errCB == null) {
        errCB = new DefaultErrorCallback();
      }
      return errCB;
    }
    public void onError (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }
}
