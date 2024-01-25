/*** JAVA ***/
public class Demo {
  public static void main(String... args) {
    // Java allows **both** of these calls - whether you want this or not.
    prettyPrintPair(new Foo(1234), new Foo(56678));
    prettyPrintPair(new Foo(1234), new Bar("some string"));
  }

  static void prettyPrintPair(Stringify x, Stringify y) {
    System.out.println("First:" + x.displayStr());
    System.out.println("Second:" + x.displayStr());
  }
}