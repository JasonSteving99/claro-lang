// Generated from /private/var/tmp/_bazel_jasonsteving/efcd1bf992362b57bda2d1a8112007a7/execroot/_main/antlr/HelloLexer.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class HelloLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		INT=1, FLOAT=2, PRINT=3, PLUS=4, MINUS=5, LPAR=6, RPAR=7, SEMICOLON=8, 
		COLON=9, VAR=10, EQ=11, DOT=12, DOUBLE_QUOTE=13, ID=14, WS=15, COMMENT=16, 
		DIGITS=17, TEXT=18;
	public static final int
		String=1;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE", "String"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"INT", "FLOAT", "PRINT", "PLUS", "MINUS", "LPAR", "RPAR", "SEMICOLON", 
			"COLON", "VAR", "EQ", "DOT", "DOUBLE_QUOTE", "ID", "WS", "COMMENT", "DIGITS", 
			"TEXT", "DOUBLE_QUOTE_IN_STRING"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'int'", "'float'", "'print'", "'+'", "'-'", "'('", "')'", "';'", 
			"':'", "'var'", "'='", "'.'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "INT", "FLOAT", "PRINT", "PLUS", "MINUS", "LPAR", "RPAR", "SEMICOLON", 
			"COLON", "VAR", "EQ", "DOT", "DOUBLE_QUOTE", "ID", "WS", "COMMENT", "DIGITS", 
			"TEXT"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public HelloLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "HelloLexer.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u0012t\u0006\uffff\uffff\u0006\uffff\uffff\u0002\u0000\u0007"+
		"\u0000\u0002\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007"+
		"\u0003\u0002\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007"+
		"\u0006\u0002\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n"+
		"\u0007\n\u0002\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002"+
		"\u000e\u0007\u000e\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002"+
		"\u0011\u0007\u0011\u0002\u0012\u0007\u0012\u0001\u0000\u0001\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001"+
		"\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007\u0001"+
		"\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b"+
		"\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0004\rR\b\r\u000b"+
		"\r\f\rS\u0001\u000e\u0004\u000eW\b\u000e\u000b\u000e\f\u000eX\u0001\u000e"+
		"\u0001\u000e\u0001\u000f\u0001\u000f\u0005\u000f_\b\u000f\n\u000f\f\u000f"+
		"b\t\u000f\u0001\u000f\u0001\u000f\u0001\u0010\u0004\u0010g\b\u0010\u000b"+
		"\u0010\f\u0010h\u0001\u0011\u0004\u0011l\b\u0011\u000b\u0011\f\u0011m"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0000\u0000"+
		"\u0013\u0002\u0001\u0004\u0002\u0006\u0003\b\u0004\n\u0005\f\u0006\u000e"+
		"\u0007\u0010\b\u0012\t\u0014\n\u0016\u000b\u0018\f\u001a\r\u001c\u000e"+
		"\u001e\u000f \u0010\"\u0011$\u0012&\u0000\u0002\u0000\u0001\u0005\u0001"+
		"\u0000az\u0003\u0000\t\n\r\r  \u0002\u0000\n\n\r\r\u0001\u000009\u0002"+
		"\u0000\"\"\\\\w\u0000\u0002\u0001\u0000\u0000\u0000\u0000\u0004\u0001"+
		"\u0000\u0000\u0000\u0000\u0006\u0001\u0000\u0000\u0000\u0000\b\u0001\u0000"+
		"\u0000\u0000\u0000\n\u0001\u0000\u0000\u0000\u0000\f\u0001\u0000\u0000"+
		"\u0000\u0000\u000e\u0001\u0000\u0000\u0000\u0000\u0010\u0001\u0000\u0000"+
		"\u0000\u0000\u0012\u0001\u0000\u0000\u0000\u0000\u0014\u0001\u0000\u0000"+
		"\u0000\u0000\u0016\u0001\u0000\u0000\u0000\u0000\u0018\u0001\u0000\u0000"+
		"\u0000\u0000\u001a\u0001\u0000\u0000\u0000\u0000\u001c\u0001\u0000\u0000"+
		"\u0000\u0000\u001e\u0001\u0000\u0000\u0000\u0000 \u0001\u0000\u0000\u0000"+
		"\u0000\"\u0001\u0000\u0000\u0000\u0001$\u0001\u0000\u0000\u0000\u0001"+
		"&\u0001\u0000\u0000\u0000\u0002(\u0001\u0000\u0000\u0000\u0004,\u0001"+
		"\u0000\u0000\u0000\u00062\u0001\u0000\u0000\u0000\b8\u0001\u0000\u0000"+
		"\u0000\n:\u0001\u0000\u0000\u0000\f<\u0001\u0000\u0000\u0000\u000e>\u0001"+
		"\u0000\u0000\u0000\u0010@\u0001\u0000\u0000\u0000\u0012B\u0001\u0000\u0000"+
		"\u0000\u0014D\u0001\u0000\u0000\u0000\u0016H\u0001\u0000\u0000\u0000\u0018"+
		"J\u0001\u0000\u0000\u0000\u001aL\u0001\u0000\u0000\u0000\u001cQ\u0001"+
		"\u0000\u0000\u0000\u001eV\u0001\u0000\u0000\u0000 \\\u0001\u0000\u0000"+
		"\u0000\"f\u0001\u0000\u0000\u0000$k\u0001\u0000\u0000\u0000&o\u0001\u0000"+
		"\u0000\u0000()\u0005i\u0000\u0000)*\u0005n\u0000\u0000*+\u0005t\u0000"+
		"\u0000+\u0003\u0001\u0000\u0000\u0000,-\u0005f\u0000\u0000-.\u0005l\u0000"+
		"\u0000./\u0005o\u0000\u0000/0\u0005a\u0000\u000001\u0005t\u0000\u0000"+
		"1\u0005\u0001\u0000\u0000\u000023\u0005p\u0000\u000034\u0005r\u0000\u0000"+
		"45\u0005i\u0000\u000056\u0005n\u0000\u000067\u0005t\u0000\u00007\u0007"+
		"\u0001\u0000\u0000\u000089\u0005+\u0000\u00009\t\u0001\u0000\u0000\u0000"+
		":;\u0005-\u0000\u0000;\u000b\u0001\u0000\u0000\u0000<=\u0005(\u0000\u0000"+
		"=\r\u0001\u0000\u0000\u0000>?\u0005)\u0000\u0000?\u000f\u0001\u0000\u0000"+
		"\u0000@A\u0005;\u0000\u0000A\u0011\u0001\u0000\u0000\u0000BC\u0005:\u0000"+
		"\u0000C\u0013\u0001\u0000\u0000\u0000DE\u0005v\u0000\u0000EF\u0005a\u0000"+
		"\u0000FG\u0005r\u0000\u0000G\u0015\u0001\u0000\u0000\u0000HI\u0005=\u0000"+
		"\u0000I\u0017\u0001\u0000\u0000\u0000JK\u0005.\u0000\u0000K\u0019\u0001"+
		"\u0000\u0000\u0000LM\u0005\"\u0000\u0000MN\u0001\u0000\u0000\u0000NO\u0006"+
		"\f\u0000\u0000O\u001b\u0001\u0000\u0000\u0000PR\u0007\u0000\u0000\u0000"+
		"QP\u0001\u0000\u0000\u0000RS\u0001\u0000\u0000\u0000SQ\u0001\u0000\u0000"+
		"\u0000ST\u0001\u0000\u0000\u0000T\u001d\u0001\u0000\u0000\u0000UW\u0007"+
		"\u0001\u0000\u0000VU\u0001\u0000\u0000\u0000WX\u0001\u0000\u0000\u0000"+
		"XV\u0001\u0000\u0000\u0000XY\u0001\u0000\u0000\u0000YZ\u0001\u0000\u0000"+
		"\u0000Z[\u0006\u000e\u0001\u0000[\u001f\u0001\u0000\u0000\u0000\\`\u0005"+
		"#\u0000\u0000]_\b\u0002\u0000\u0000^]\u0001\u0000\u0000\u0000_b\u0001"+
		"\u0000\u0000\u0000`^\u0001\u0000\u0000\u0000`a\u0001\u0000\u0000\u0000"+
		"ac\u0001\u0000\u0000\u0000b`\u0001\u0000\u0000\u0000cd\u0006\u000f\u0001"+
		"\u0000d!\u0001\u0000\u0000\u0000eg\u0007\u0003\u0000\u0000fe\u0001\u0000"+
		"\u0000\u0000gh\u0001\u0000\u0000\u0000hf\u0001\u0000\u0000\u0000hi\u0001"+
		"\u0000\u0000\u0000i#\u0001\u0000\u0000\u0000jl\b\u0004\u0000\u0000kj\u0001"+
		"\u0000\u0000\u0000lm\u0001\u0000\u0000\u0000mk\u0001\u0000\u0000\u0000"+
		"mn\u0001\u0000\u0000\u0000n%\u0001\u0000\u0000\u0000op\u0005\"\u0000\u0000"+
		"pq\u0001\u0000\u0000\u0000qr\u0006\u0012\u0002\u0000rs\u0006\u0012\u0003"+
		"\u0000s\'\u0001\u0000\u0000\u0000\u0007\u0000\u0001SX`hm\u0004\u0005\u0001"+
		"\u0000\u0000\u0001\u0000\u0007\r\u0000\u0004\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}