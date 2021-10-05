// Copyright 2018 Google LLC.
// SPDX-License-Identifier: Apache-2.0

package com.claro;

/**
 * An exception from the lexer/parser.
 */
public class ClaroParserException extends RuntimeException {
  public ClaroParserException(String message) {
    super(message);
  }
}
