package com.claro.stdlib;

public class StdLibModuleRegistry {

  // Because Claro gives special semantic meaning to certain stdlib types such as `Error`, I want to ensure that this
  // special language-level semantic is given *only* to that stdlib type, not just any old type named `Error`. Hence,
  // here I hardcode the module disambiguator for the stdlib, so that it can be checked against.
  public static final String STDLIB_MODULE_PACKAGE = "claro.lang";
  public static final String STDLIB_MODULE_DISAMBIGUATOR = "stdlib$std";
}
