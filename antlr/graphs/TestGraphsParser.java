package claro.lang;

import com.google.devtools.common.options.OptionsParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class TestGraphsParser {
  static String CLARO_FILE;
  static String OUTPUT_FILE;

  public static void main(String[] args) throws IOException, InterruptedException {
    // Parse CLI Options.
    parseCLIOptions(args);

    // Lex and parse the input file.
    InputStream input = readFile(CLARO_FILE);
    CharStream inputStream = CharStreams.fromStream(input);
    GraphsLexer lexer = new GraphsLexer(inputStream);
    TokenStream tokenStream = new CommonTokenStream(lexer);
    GraphsParser graphsParser = new GraphsParser(tokenStream);

    // Configure errors to throw an exception since we don't want to try processing syntactically invalid programs.
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
    graphsParser.removeErrorListeners();
    graphsParser.addErrorListener(ThrowingErrorListener.INSTANCE);

    // Parse the actual grammar.
    GraphsParser.StartContext tree = null;
    try {
      tree = graphsParser.start();
    } catch (ParseCancellationException e) {
      System.err.println("Encountered error while parsing:\n\t" + e.getMessage());
      System.exit(1);
    }

    // You can walk the tree easily by using a listener.
    GraphsListenerImpl listener = new GraphsListenerImpl();
    new ParseTreeWalker().walk(listener, tree);

    try (FileWriter outputFileWriter = new FileWriter(createOutputFile(OUTPUT_FILE))) {
      outputFileWriter.write(listener.graphs.toString());
    }
  }

  private static File createOutputFile(String path) {
    File outputFile = null;
    try {
      outputFile = new File(path);
      outputFile.createNewFile();
    } catch (IOException e) {
      System.err.println("An error occurred while trying to open/create the specified output file: " + path);
      e.printStackTrace();
      System.exit(1);
    }
    return outputFile;
  }

  private static void parseCLIOptions(String[] args) {
    OptionsParser parser = OptionsParser.newOptionsParser(TestGraphsParserCLIOptions.class);
    parser.parseAndExitUponError(args);
    TestGraphsParserCLIOptions options = parser.getOptions(TestGraphsParserCLIOptions.class);
    CLARO_FILE = options.claro_file;
    if (CLARO_FILE == null || CLARO_FILE.length() == 0) {
      System.err.println("Claro file is required to be set using `-f` or `--claro_file`.");
      System.exit(1);
    }
    OUTPUT_FILE = options.output_file;
    if (OUTPUT_FILE == null || OUTPUT_FILE.length() == 0) {
      System.err.println("Output file is required to be set using `-o` or `--output_file`.");
      System.exit(1);
    }
  }

  static class GraphsListenerImpl extends GraphsParserBaseListener {
    final StringBuilder graphs = new StringBuilder();

    @Override
    public void enterGraph(GraphsParser.GraphContext ctx) {
      this.graphs
          .append("\n```mermaid\n---\ntitle: ")
          .append(ctx.ID().getText())
          .append("\n---\n")
          .append("graph TD\n");
    }

    @Override
    public void exitGraph(GraphsParser.GraphContext ctx) {
      graphs.append("```");
    }

    @Override
    public void enterNodes(GraphsParser.NodesContext ctx) {
      if (ctx.NODE_REF_ID() == null) {
        graphs.append(ctx.ID().getText());
      } else {
        for (TerminalNode nodeRefId : ctx.NODE_REF_ID()) {
          graphs.append("\t").append(nodeRefId.getText()).append(" --> ").append(ctx.ID().getText()).append("\n");
        }
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
    System.out.println(obj);
  }

  static InputStream readFile(String path) throws IOException {
    return Files.newInputStream(FileSystems.getDefault().getPath(path), StandardOpenOption.READ);
  }

}