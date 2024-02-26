package hello.world;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.IOException;
import java.io.InputStream;

public class TestParser {

  public static void main(String[] args) throws IOException {
    // Lex and parse the input file.
    InputStream input = TestParser.class.getResourceAsStream("/antlr/test.hello");
    CharStream inputStream = CharStreams.fromStream(input);
    HelloLexer lexer = new HelloLexer(inputStream);
    TokenStream tokenStream = new CommonTokenStream(lexer);
    HelloParser helloParser = new HelloParser(tokenStream);

    // Configure errors to throw an exception since we don't want to try processing syntactically invalid programs.
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
    helloParser.removeErrorListeners();
    helloParser.addErrorListener(ThrowingErrorListener.INSTANCE);

    // Parse the actual grammar.
    HelloParser.StartContext tree = null;
    try {
      tree = helloParser.start();
    } catch (ParseCancellationException e) {
      System.err.println("Encountered error while parsing:\n\t" + e.getMessage());
      System.exit(1);
    }

    ParseTreeWalker walker = new ParseTreeWalker();
    for (int i = 0; i < tree.getChildCount(); i++) {
      walker.walk(new HelloListenerImpl(tokenStream), tree.getChild(i));
    }

    // You can grab a list of all children of a certain node type.
    System.err.println(tree.var_decl(1).getText());

    // You can walk the tree easily by using a visitor.
//    new ParseTreeWalker().walk(new HelloListenerImpl(), tree);
  }

  static class HelloListenerImpl extends HelloParserBaseListener {
    private final TokenStream tokenStream;
    HelloListenerImpl(TokenStream tokenStream) {
      this.tokenStream = tokenStream;
    }
    @Override
    public void enterVar_decl(HelloParser.Var_declContext ctx) {
      print("FOUND Var Declaration: (" + tokenStream.getText(ctx.ID().getSourceInterval()) + ")");
      print(ctx.toStringTree());
      print(ctx.type().getText());
    }

    @Override
    public void enterExpr(HelloParser.ExprContext ctx) {
      if (ctx.PLUS() != null) {
        System.err.println("FOUND ADDITION: " + tokenStream.getText(ctx.getSourceInterval()));
      }
    }
  }

  static class ThrowingErrorListener extends BaseErrorListener {

    public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e)
        throws ParseCancellationException {
      throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg);
    }
  }


  static void print(Object obj) {
    System.err.println(obj);
  }

}