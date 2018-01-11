#!/usr/bin/env groovy
package com.concur

class Constants {
  static class Colors {
    public static String CLEAR    = "\u001B[0m"

    public static String RED      = "\u001B[31m"
    public static String GREEN    = "\u001B[32m"
    public static String YELLOW   = "\u001B[33m"
    public static String BLUE     = "\u001B[34m"
    public static String MAGENTA  = "\u001B[35m"
    public static String CYAN     = "\u001B[36m"
    public static String ORANGE   = '\u001B[38;2;255;165;0m'

    public static String BRIGHT_RED     = "\u001B[31;1m"
    public static String BRIGHT_GREEN   = "\u001B[32;1m"
    public static String BRIGHT_YELLOW  = "\u001B[33;1m"
    public static String BRIGHT_BLUE    = "\u001B[34;1m"
    public static String BRIGHT_MAGENTA = "\u001B[35;1m"
    public static String BRIGHT_CYAN    = "\u001B[36;1m"

    public static String RED_ON_BLACK     = '\u001B[0;31;40m'
    public static String GREEN_ON_BLACK   = '\u001B[0;32;40m'
    public static String YELLOW_ON_BLACK  = '\u001B[0;33;40m'
    public static String BLUE_ON_BLACK    = '\u001B[0;34;40m'
    public static String MAGENTA_ON_BLACK = '\u001B[0;35;40m'
    public static String CYAN_ON_BLACK    = '\u001B[0;36;40m'
    public static String WHITE_ON_BLACK   = '\u001B[0;37;40m'
    public static String ORANGE_ON_BLACK  = '\u001B[38;5;214;48;5;0m'
  }
  static class Strings {
    // Special Characters/ANSI Codes
    public static String DIAMOND_WITH_X = '\u2756'

    // WorkflowLibs related
    public static String WORKFLOWLIBS_GITHUB_PROJECT = 'https://github.com/concur/jenkins-yml-workflowLibs'
    public static String WORKFLOWLIBS_GITHUB_RELASES = 'https://github.com/concur/jenkins-yml-workflowLibs/releases'

    // Workflows related
    public static String WORKFLOWS_REFER_TO_DOCUMENTATION = 'Please see the documentation for proper usage of this workflow.'
    public static String WORKFLOWS_GITHUB_PROJECT = 'https://github.com/concur/jenkins-workflows'
    public static String WORKFLOWS_GITHUB_RELASES = 'https://github.com/concur/jenkins-workflows/releases'

    public static String WORKFLOWS_DEPRECATED_METHOD = "${Colors.ORANGE_ON_BLACK}Usage of this method has been deprecated, please refer to the documentation for information on migrating your pipeline.${Colors.CLEAR}"
  }
  class Env {
    public static String VERSION      = "BUILDHUB_VERSION"
    public static String DEBUG        = "DEBUG_MODE"
    public static String DEBUG_LEVEL  = "DEBUG_LEVEL"
    public static String DATE_FORMAT  = "DATE_FORMAT"
  }
}
