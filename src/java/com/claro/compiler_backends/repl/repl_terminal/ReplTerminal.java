package com.claro.compiler_backends.repl.repl_terminal;

import com.google.devtools.build.runfiles.Runfiles;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal.CtrlCBehaviour;
import com.googlecode.lanterna.terminal.ansi.UnixTerminal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.util.function.Function;

public class ReplTerminal {

  private static String claroVersion = "v?.?.?";

  private final Function<String, Void> inputHandler;

  static {
    try {
      String path = Runfiles.create().rlocation("claro-lang/CLARO_VERSION.txt");
      BufferedReader reader = new BufferedReader(new FileReader(path));
      claroVersion = reader.readLine().trim();
    } catch (Exception ignored) {
      // If for some reason this file isn't where it should be, we'll try one last thing
      // looking in the pwd for the file. This should be used for the Riju-hosted version
      // whose binary is run outside of Bazel's sandbox. The GitHub Action that releases
      // Claro artifacts will have placed a CLARO_VERSION.txt file in the claro_programs/
      // dir before creating the tarball, and then the Riju Claro config script copies that
      // whole dir to the directory where the Claro REPL will be executed from...
      // TODO(steving) Come up with a more portable solution. Figure out how to bundle the
      //  version file into the deploy jar itself.
      try {
        BufferedReader reader = new BufferedReader(new FileReader("programs/CLARO_VERSION.txt"));
        claroVersion = reader.readLine().trim();
      } catch (Exception ignoredAgain) {
        // Turns out we still don't know where the version file is, but don't let this break
        // things, just keep the default version label.
      }
    }
  }

  public ReplTerminal(Function<String, Void> inputHandler) {
    this.inputHandler = inputHandler;

  }

  public void runTerminal() {
    DefaultTerminalFactory defaultTerminalFactory = new DefaultTerminalFactory();
    UnixTerminal terminal = null;
    try {
      terminal = new UnixTerminal(
          System.in,
          System.out,
          StandardCharsets.UTF_8,
          CtrlCBehaviour.CTRL_C_KILLS_APPLICATION);

      terminal.clearScreen();
      terminal.setCursorVisible(true);

      final TextGraphics textGraphics = terminal.newTextGraphics();

      // Change to a background/foreground color combo that pops more.
      textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
      textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
      textGraphics.putString(2, 0, String.format("ClaroLang %s - Press ESC/Ctr-C to exit", claroVersion), SGR.BOLD);

      // Change back to the default background/foreground colors.
      textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
      textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);

      final String PROMPT = ">>> ";
      textGraphics.putString(0, 1, PROMPT, SGR.BOLD);
      // Need to flush for changes to become visible.
      terminal.flush();

      // Setup the control vars that'll track the current user position and state as they use the terminal.
      StringBuilder currInstruction = new StringBuilder();
      Stack<String> prevInstructions = new Stack<>();
      int instructionIndex = 0;
      int currLine = 1;
      int currPromptCol = 0;

      KeyStroke keyStroke = terminal.readInput();
      while (keyStroke.getKeyType() != KeyType.Escape) {
        switch (keyStroke.getKeyType()) {
          case Character:
            Character currKeyStrokeCharacter = keyStroke.getCharacter();
            currInstruction.insert(currPromptCol, keyStroke.getCharacter().charValue());
            currPromptCol++;
            break;
          case ArrowUp:
            if (instructionIndex == 0) {
              terminal.bell();
            } else {
              textGraphics.putString(0, currLine, PROMPT + getSpacesStringOfLength(currInstruction.length()));
              currInstruction.delete(0, currInstruction.length());
              currInstruction.append(prevInstructions.elementAt(--instructionIndex));
              currPromptCol = currInstruction.length();
            }
            break;
          case ArrowDown:
            if (instructionIndex == prevInstructions.size() - 1) {
              // Moving down after the last instruction, should just clear everything.
              textGraphics.putString(0, currLine, PROMPT + getSpacesStringOfLength(currInstruction.length()));
              currInstruction.delete(0, currInstruction.length());
              instructionIndex++;
              currPromptCol = 0;
            } else if (instructionIndex >= prevInstructions.size()) {
              terminal.bell();
            } else {
              textGraphics.putString(0, currLine, PROMPT + getSpacesStringOfLength(currInstruction.length()));
              currInstruction.delete(0, currInstruction.length());
              currInstruction.append(prevInstructions.elementAt(++instructionIndex));
              currPromptCol = currInstruction.length();
            }
            break;
          case ArrowRight:
            if (currPromptCol < currInstruction.length()) {
              currPromptCol++;
            } else {
              terminal.bell();
            }
            break;
          case ArrowLeft:
            if (currPromptCol > 0) {
              currPromptCol--;
            } else {
              terminal.bell();
            }
            break;
          case Backspace:
            if (currPromptCol > 0) {
              // Overwrite the buffer so that after modifying the currInstruction, the terminal will show the correctly
              // edited instruction.
              textGraphics.putString(0, currLine, getSpacesStringOfLength(PROMPT.length() + currInstruction.length()));
              currInstruction.deleteCharAt(currPromptCol - 1);
              currPromptCol--;
            } else {
              terminal.bell();
            }
            break;
          case Enter:
            if (currInstruction.length() > 0) {
              prevInstructions.push(currInstruction.toString());
              instructionIndex = prevInstructions.size();
              currInstruction.delete(0, currInstruction.length());

              // TODO(steving) This approach won't work once iteration is implemented in Claro because
              // TODO(steving) printing doesn't refresh the terminal until ALL printing is done w/in the
              // TODO(steving) loop. So in the loop `while (True) { print("asdf"); }`, there would be no
              // TODO(steving) updates to the terminal since the terminal would be waiting for the inputHandler
              // TODO(steving) callback to finish before refreshing. Instead, should be plumbing this
              // TODO(steving) TextGraphics/Terminal logic into the REPL impl of the PrintStmt node.
              // Finally, actually call the given inputHandler.
              // Setup the cursor for potential output from inputHandler.
              if (++currLine >= terminal.getTerminalSize().getRows()) {
                terminal.scrollLines(0, currLine - 1, 1);
              }
              terminal.setCursorPosition(0, currLine);
              inputHandler.apply(prevInstructions.peek());
              currLine = terminal.getCursorPosition().getRow();
            } else {
              currLine++;
            }
            if (currLine >= terminal.getTerminalSize().getRows()) {
              /*
               Note that currLine is potentially pointing ahead of the last row shown in the terminal
               currently because the inputHandler may have output multiple lines. In this case, this
               scrollLines method is being told to scroll lines that are currently below the fold, but
               it appears that it handles this well.
              */
              terminal.scrollLines(0, currLine, 1);
            }
            currPromptCol = 0;
            break;
        }

        // Print the currInstruction in two parts to leave the cursor exactly where the user expects it.
        textGraphics.putString(0, currLine, PROMPT + currInstruction.toString(), SGR.BOLD);
        textGraphics.putString(0, currLine, PROMPT + currInstruction.substring(0, currPromptCol));
        terminal.flush();
        keyStroke = terminal.readInput();
      }

    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (terminal != null) {
        try {
          terminal.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private static String getSpacesStringOfLength(int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length must be > 0.");
    }
    StringBuilder spacesString = new StringBuilder();
    for (int i = 0; i < length; i++) {
      spacesString.append(" ");
    }
    return spacesString.toString();
  }
}
