/*** JAVA ***/
$$import java.util.List;
$$import java.util.ArrayList;
$$import java.lang.StringBuilder;
$$
interface Stringify {
  String displayStr();
}
$$
class Foo implements Stringify {
  // ...
$$  private final int wrapped;
$$  public Foo(int wrapped) {
$$    this.wrapped = wrapped;
$$  }
$$
  @Override
  public String displayStr() {
    // ...
$$    String boundingLine = Util.repeated('*', String.valueOf(this.wrapped).length() + "* Foo() *".length());
$$    return String.format("%s\n* Foo(%s) *\n%s", boundingLine, this.wrapped, boundingLine);
  }
}
$$
class Bar implements Stringify {
  // ...
$$  private final String wrapped;
$$  public Bar(String wrapped) {
$$    this.wrapped = wrapped;
$$  }
$$
  @Override
  public String displayStr() {
    // ...
$$    String boundingLine = Util.repeated('-', this.wrapped.length() + "| Bar() |".length());
$$    return String.format("%s\n| Foo(%s) |\n%s", boundingLine, this.wrapped, boundingLine);
  }
}
$$
class Buzz implements Stringify {
  // ...
$$  private final String wrapped;
$$  public Buzz(String wrapped) {
$$    this.wrapped = wrapped;
$$  }
$$
  @Override
  public String displayStr() {
    // ...
$$    String boundingLine = Util.repeated('#', this.wrapped.length() + "# Buzz() #".length());
$$    return String.format("%s\n# Buzz(%s) #\n%s", boundingLine, this.wrapped, boundingLine);
  }
}
$$
$$class Util {
$$  public static String repeated(char c, int n) {
$$    StringBuilder sb = new StringBuilder();
$$    for (; n > 0; n--) {
$$      sb.append(c);
$$    }
$$    return sb.toString();
$$  }
$$}
$$