package com.claro.stdlib.userinput;

import java.util.Scanner;

public class UserInput {
  public static final Scanner INPUT_SCANNER = new Scanner(System.in);

  public static String promptUserInput(String prompt) {
    System.out.println(prompt);
    return INPUT_SCANNER.nextLine();
  }
}
