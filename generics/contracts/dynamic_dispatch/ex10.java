public class Demo {
  public static void main(String... args) {
    ArrayList<Stringify> elems = new ArrayList<>();
    elems.add(new Foo(1234));
    elems.add(new Bar("some string"));
    elems.add(new Buzz("another"));
    prettyPrintList(elems);
  }

  static void prettyPrintList(ArrayList<Stringify> l) {
    for (Stringify e : l) {
      System.out.println(e.displayStr());
    }
  }
}