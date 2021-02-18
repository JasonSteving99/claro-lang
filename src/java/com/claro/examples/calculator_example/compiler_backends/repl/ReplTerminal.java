package com.claro.examples.calculator_example.compiler_backends.repl;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal.CtrlCBehaviour;

import java.io.IOException;
import java.util.Stack;
import java.util.function.Function;

public class ReplTerminal {

  private final Function<String, Void> inputHandler;

  ReplTerminal(Function<String, Void> inputHandler) {
    this.inputHandler = inputHandler;
  }

  public void runTerminal() {
    DefaultTerminalFactory defaultTerminalFactory = new DefaultTerminalFactory();
    Terminal terminal = null;
    try {
      terminal = defaultTerminalFactory
          .setUnixTerminalCtrlCBehaviour(CtrlCBehaviour.CTRL_C_KILLS_APPLICATION)
          .createTerminal();

      terminal.clearScreen();
      terminal.setCursorVisible(true);

      final TextGraphics textGraphics = terminal.newTextGraphics();

      // Change to a background/foreground color combo that pops more.
      textGraphics.setForegroundColor(TextColor.ANSI.WHITE);
      textGraphics.setBackgroundColor(TextColor.ANSI.BLACK);
      textGraphics.putString(2, 1, "ClaroLang 0.0.1 - Press ESC/Ctr-C to exit", SGR.BOLD);

      // Change back to the default background/foreground colors.
      textGraphics.setForegroundColor(TextColor.ANSI.DEFAULT);
      textGraphics.setBackgroundColor(TextColor.ANSI.DEFAULT);

      final String PROMPT = ">>> ";
      textGraphics.putString(0, 2, PROMPT, SGR.BOLD);
      // Need to flush for changes to become visible.
      terminal.flush();

      // Setup the control vars that'll track the current user position and state as they use the terminal.
      StringBuilder currInstruction = new StringBuilder();
      Stack<String> prevInstructions = new Stack<>();
      int instructionIndex = 0;
      int currLine = 2;
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
              terminal.setCursorPosition(0, currLine + 1);
              inputHandler.apply(prevInstructions.peek());
              currLine = terminal.getCursorPosition().getRow();
            } else {
              currLine++;
            }
            currPromptCol = 0;
            break;
        }
        // TODO(steving) Need to allow user to hit enter enough times to have previous instructions scroll
        // TODO(steving) off the screen. This would involve printing out the top N things on the insctruction
        // TODO(steving) stack where N is the number of rows in the terminal window. This'll also require
        // TODO(steving) maintaining a new stack of the outputs which would be gathered using
        // TODO(steving) TextGraphics::getCharacter(col, row).
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
