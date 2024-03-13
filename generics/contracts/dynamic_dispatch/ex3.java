/*** JAVA ***/
public class Demo {
  public static void main(String... args) {
    // Foo, Bar, and Buzz are all "subtypes" of Stringify.
    prettyPrint(new Foo(1234));
    prettyPrint(new Bar("some string"));
    prettyPrint(new Buzz("another"));
  }

  static void prettyPrint(Stringify x) {
    System.out.println(x.displayStr());
  }
}