package org.tron.common.zksnark;

import org.tron.common.util.Utils;

import java.io.IOException;

public class LibsodiumWrapper {
  private static final Libsodium INSTANCE = new Libsodium();

  static {
    try {
      System.load(Utils.getLibraryByName("libzksnarkjni"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Libsodium getInstance() {
    return INSTANCE;
  }

}
