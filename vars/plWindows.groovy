#!/usr/bin/groovy
// vars/bhWindows.groovy
def call(duration = 1, unit = "HOURS", Closure body) {

  def concurPipeline = new com.concur.Commands()

  node('windows') {
    duration = duration ?: 1
    unit = unit ?: 'HOURS'
    assert (duration instanceof Integer) : "duration must be a Integer"
    assert (unit instanceof String) : "unit must be a String"
    timeout(time: duration, unit: unit.toUpperCase()) {
      ansiColor('xterm') {
        concurPipeline.debugPrint(['duration' : duration, 'unit' : unit])
        def dirName = "C:\\jenkins\\workspace\\${UUID.randomUUID().toString().take(18)}"
        echo "\u2756 Using \u001B[35m ${dirName} \u001B[0m as the path for the workspace."
        try {
          // Clean the workspace name so that \ characters that are encoded to 2%F don't break the path
          // for execution. Shorten the directory length and use a uuid for the dir
          ws(dirName) {
            try {
              body()
            } finally {
              // Delete the new workspace after it's done
              echo "\u2756 Deleting the \u001B[35m ${pwd()} \u001B[0m workspace."
              dir(pwd()) {
                deleteDir()
              }
            }
          }
        } catch (e) {
          throw e
        } finally {
          cleanWs notFailBuild: true
        }
      }
    }
  }
}
