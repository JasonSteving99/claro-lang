package com.claro.runtime_utilities.injector;

import java.util.LinkedHashMap;

/**
 * The entire purpose of this class is basically just to have a single known place to statically
 * register a Map that will be used as the "Injector" for all dependencies in Claro functions.
 * This is an extremely minimal structure, literally just serving the purpose of centrally marking
 * down the bindings that are configured by Modules and giving Claro access to inject dependencies
 * from a single centralized location.
 */
public class Injector {
  public static LinkedHashMap<Key, Object> bindings = new LinkedHashMap<>();
}



