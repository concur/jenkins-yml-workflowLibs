#!/usr/bin/env groovy
package com.concur

public class Constants {
  public class Colors {
    public static String CLEAR    = "\u001B[0m"
    public static String RED      = "\u001B[31m"
    public static String GREEN    = "\u001B[32m"
    public static String YELLOW   = "\u001B[33m"
    public static String BLUE     = "\u001B[34m"
    public static String MAGENTA  = "\u001B[35m"
    public static String CYAN     = "\u001B[36m"

    public static String BRIGHT_RED     = "\u001B[31;1m"
    public static String BRIGHT_GREEN   = "\u001B[32;1m"
    public static String BRIGHT_YELLOW  = "\u001B[33;1m"
    public static String BRIGHT_BLUE    = "\u001B[34;1m"
    public static String BRIGHT_MAGENTA = "\u001B[35;1m"
    public static String BRIGHT_CYAN    = "\u001B[36;1m"

    public static String RED_ON_BLACK     = "\u001B[0;31;40m"
    public static String GREEN_ON_BLACK   = "\u001B[0;32;40m"
    public static String YELLOW_ON_BLACK  = "\u001B[0;33;40m"
    public static String BLUE_ON_BLACK    = "\u001B[0;34;40m"
    public static String MAGENTA_ON_BLACK = "\u001B[0;35;40m"
    public static String CYAN_ON_BLACK    = "\u001B[0;36;40m"
  }
  public class Env {
    public static String VERSION      = "BUILDHUB_VERSION"
    public static String DEBUG        = "DEBUG_MODE"
    public static String DEBUG_LEVEL  = "DEBUG_LEVEL"
    public static String DATE_FORMAT  = "DATE_FORMAT"
  }
}
