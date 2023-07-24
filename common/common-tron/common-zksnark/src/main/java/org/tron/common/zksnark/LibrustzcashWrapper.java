package org.tron.common.zksnark;

import org.tron.common.util.Utils;

import java.io.IOException;

public class LibrustzcashWrapper {
  private static final Librustzcash INSTANCE = new Librustzcash();

  static {
    try {
      System.load(Utils.getLibraryByName("libzksnarkjni"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Librustzcash getInstance() {
    return INSTANCE;
  }

}
