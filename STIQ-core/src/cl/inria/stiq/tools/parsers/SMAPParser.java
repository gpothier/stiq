/* Generated By:JavaCC: Do not edit this line. SMAPParser.java */
package cl.inria.stiq.tools.parsers;

import java.util.*;

import cl.inria.stiq.tools.parsers.smap.*;
import cl.inria.stiq.tools.parsers.smap.LineSection.InputLineInfo;
import cl.inria.stiq.tools.parsers.smap.LineSection.LineInfo;
import cl.inria.stiq.tools.parsers.smap.LineSection.OutputLineInfo;
import cl.inria.stiq.tools.parsers.smap.LineSection.StartLine;

public class SMAPParser implements SMAPParserConstants {

  static final public String string() throws ParseException {
        Token t;
    t = jj_consume_token(STRING);
                {if (true) return t.image;}
    throw new Error("Missing return statement in function");
  }

  static final public int number() throws ParseException {
        String s;
    s = string();
                {if (true) return Integer.parseInt(s);}
    throw new Error("Missing return statement in function");
  }

  static final public SMAP smap() throws ParseException {
        AbstractSection sect;
        List sections = new ArrayList();
        String outputFileName;
        String defaultStratum;
    jj_consume_token(SMAP);
    jj_consume_token(CR);
    outputFileName = string();
    jj_consume_token(CR);
    defaultStratum = string();
    jj_consume_token(CR);
    label_1:
    while (true) {
      sect = section();
                                          if (sect != null) sections.add(sect);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STAR_STRATUM:
      case STAR_FILE:
      case STAR_LINE:
      case STAR_OTHER:
        ;
        break;
      default:
        jj_la1[0] = jj_gen;
        break label_1;
      }
    }
    jj_consume_token(STAR_END);
    jj_consume_token(CR);
                {if (true) return new SMAP(outputFileName, defaultStratum, sections);}
    throw new Error("Missing return statement in function");
  }

  static final public AbstractSection section() throws ParseException {
        AbstractSection s;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case STAR_STRATUM:
      s = stratumSection();
      break;
    case STAR_FILE:
      s = fileSection();
      break;
    case STAR_LINE:
      s = lineSection();
      break;
    case STAR_OTHER:
      s = otherSection();
      break;
    default:
      jj_la1[1] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
                {if (true) return s;}
    throw new Error("Missing return statement in function");
  }

  static final public AbstractSection stratumSection() throws ParseException {
        String id;
    jj_consume_token(STAR_STRATUM);
    id = string();
    jj_consume_token(CR);
                {if (true) return new StratumSection(id);}
    throw new Error("Missing return statement in function");
  }

  static final public AbstractSection fileSection() throws ParseException {
        FileInfo info;
        List infos = new ArrayList();
    jj_consume_token(STAR_FILE);
    jj_consume_token(CR);
    label_2:
    while (true) {
      info = fileInfo();
                                            infos.add(info);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STRING:
      case 13:
        ;
        break;
      default:
        jj_la1[2] = jj_gen;
        break label_2;
      }
    }
                {if (true) return new FileSection(infos);}
    throw new Error("Missing return statement in function");
  }

  static final public FileInfo fileInfo() throws ParseException {
        int id;
        String name;
        String absName;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case STRING:
      id = number();
      name = string();
      jj_consume_token(CR);
                {if (true) return new FileInfo(id, name);}
      break;
    case 13:
      jj_consume_token(13);
      id = number();
      name = string();
      jj_consume_token(CR);
      absName = string();
      jj_consume_token(CR);
                {if (true) return new FileInfo(id, name, absName);}
      break;
    default:
      jj_la1[3] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
    throw new Error("Missing return statement in function");
  }

  static final public AbstractSection lineSection() throws ParseException {
        LineInfo info;
        List infos = new ArrayList();
    jj_consume_token(STAR_LINE);
    jj_consume_token(CR);
    label_3:
    while (true) {
      info = lineInfo();
                                            infos.add(info);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STRING:
        ;
        break;
      default:
        jj_la1[4] = jj_gen;
        break label_3;
      }
    }
                {if (true) return new LineSection(infos);}
    throw new Error("Missing return statement in function");
  }

  static final public LineInfo lineInfo() throws ParseException {
        InputLineInfo in;
        OutputLineInfo out;
    in = inputLineInfo();
    jj_consume_token(14);
    out = outputLineInfo();
    jj_consume_token(CR);
                {if (true) return new LineInfo(in, out);}
    throw new Error("Missing return statement in function");
  }

  static final public InputLineInfo inputLineInfo() throws ParseException {
        StartLine in;
        int rep = 1;
    in = startLine();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case 15:
      jj_consume_token(15);
      rep = number();
      break;
    default:
      jj_la1[5] = jj_gen;
      ;
    }
                {if (true) return new InputLineInfo(in, rep);}
    throw new Error("Missing return statement in function");
  }

  static final public OutputLineInfo outputLineInfo() throws ParseException {
        StartLine out;
        int inc = 1;
    out = startLine();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case 15:
      jj_consume_token(15);
      inc = number();
      break;
    default:
      jj_la1[6] = jj_gen;
      ;
    }
                {if (true) return new OutputLineInfo(out, inc);}
    throw new Error("Missing return statement in function");
  }

  static final public StartLine startLine() throws ParseException {
        int n;
        int id = -1;
    n = number();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case 16:
      jj_consume_token(16);
      id = number();
      break;
    default:
      jj_la1[7] = jj_gen;
      ;
    }
                {if (true) return new StartLine(n, id);}
    throw new Error("Missing return statement in function");
  }

  static final public AbstractSection otherSection() throws ParseException {
    jj_consume_token(STAR_OTHER);
    jj_consume_token(CR);
    label_4:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case STRING:
        ;
        break;
      default:
        jj_la1[8] = jj_gen;
        break label_4;
      }
      string();
      jj_consume_token(CR);
    }
                {if (true) return null;}
    throw new Error("Missing return statement in function");
  }

  static private boolean jj_initialized_once = false;
  static public SMAPParserTokenManager token_source;
  static SimpleCharStream jj_input_stream;
  static public Token token, jj_nt;
  static private int jj_ntk;
  static private int jj_gen;
  static final private int[] jj_la1 = new int[9];
  static private int[] jj_la1_0;
  static {
      jj_la1_0();
   }
   private static void jj_la1_0() {
      jj_la1_0 = new int[] {0xb8,0xb8,0x2100,0x2100,0x100,0x8000,0x8000,0x10000,0x100,};
   }

  public SMAPParser(java.io.InputStream stream) {
    if (jj_initialized_once) {
      System.out.println("ERROR: Second call to constructor of static parser.  You must");
      System.out.println("       either use ReInit() or set the JavaCC option STATIC to false");
      System.out.println("       during parser generation.");
      throw new Error();
    }
    jj_initialized_once = true;
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new SMAPParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 9; i++) jj_la1[i] = -1;
  }

  static public void ReInit(java.io.InputStream stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 9; i++) jj_la1[i] = -1;
  }

  public SMAPParser(java.io.Reader stream) {
    if (jj_initialized_once) {
      System.out.println("ERROR: Second call to constructor of static parser.  You must");
      System.out.println("       either use ReInit() or set the JavaCC option STATIC to false");
      System.out.println("       during parser generation.");
      throw new Error();
    }
    jj_initialized_once = true;
    jj_input_stream = new SimpleCharStream(stream, 1, 1);
    token_source = new SMAPParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 9; i++) jj_la1[i] = -1;
  }

  static public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 9; i++) jj_la1[i] = -1;
  }

  public SMAPParser(SMAPParserTokenManager tm) {
    if (jj_initialized_once) {
      System.out.println("ERROR: Second call to constructor of static parser.  You must");
      System.out.println("       either use ReInit() or set the JavaCC option STATIC to false");
      System.out.println("       during parser generation.");
      throw new Error();
    }
    jj_initialized_once = true;
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 9; i++) jj_la1[i] = -1;
  }

  public void ReInit(SMAPParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 9; i++) jj_la1[i] = -1;
  }

  static final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  static final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

  static final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  static final private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  static private java.util.Vector jj_expentries = new java.util.Vector();
  static private int[] jj_expentry;
  static private int jj_kind = -1;

  static public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[17];
    for (int i = 0; i < 17; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 9; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 17; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  static final public void enable_tracing() {
  }

  static final public void disable_tracing() {
  }

}
