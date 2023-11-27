package com.claro.module_system.module_serialization.json;

import com.claro.module_system.module_serialization.proto.SerializedClaroModule;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;

public class SerializedClaroModuleJsonConverter {

  // All we're gonna do is read in a single protobuf from stdin, convert it to a JSON string, and then print to stdout.
  public static void main(String[] args) throws IOException {
    SerializedClaroModule proto = SerializedClaroModule.parseDelimitedFrom(System.in);
    String json = JsonFormat.printer().print(proto);
    System.out.println(json);
  }
}
