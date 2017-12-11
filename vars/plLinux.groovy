#!/usr/bin/groovy
// vars/bhLinux.groovy
def call(duration = 1, unit = "HOURS", Closure body) {

  def concurPipeline = new com.concur.Commands()

  node('linux') {
    duration  = duration  ?: 1
    unit      = unit      ?: 'HOURS'
    assert (duration instanceof Integer) : "duration must be a Integer"
    assert (unit instanceof String) : "unit must be a String"

    timeout(time: duration, unit: unit.toUpperCase()) {
      ansiColor('xterm') {
        concurPipeline.debugPrint(['duration' : duration, 'unit' : unit])
        String linuxWS = pwd()
        try {
          body()
        } catch (e) {
          println "Execution error :: ${e}"
          throw e
        } finally {
          // Mount the workspace in the docker container.
          cleanWs
        }
      }
    }
  }
}
