#!/usr/bin/groovy
// vars/bhLinux.groovy
def call(String label, int duration=1, String unit="HOURS", Closure body) {

  def concurPipeline = new com.concur.Commands()

  node(label) {
    duration  = duration  ?: 1
    unit      = unit      ?: 'HOURS'
    assert (duration instanceof Integer)  : "WorkflowLibs :: plNode :: duration must be a Integer"
    assert (unit instanceof String)       : "WorkflowLibs :: plNode :: unit must be a String"

    timeout(time: duration, unit: unit.toUpperCase()) {
      ansiColor('xterm') {
        concurPipeline.debugPrint(['duration' : duration, 'unit' : unit])
        try {
          body()
        } catch (e) {
          println "Execution error :: ${e}"
          throw e
        } finally {
          String workspace = pwd()
          try {
            // cleanWs notFailBuild: true
          } catch (e) {
            if (isUnix()) {
              docker.image('alpine:3.7').inside('-u 0:0') {
                sh "rm -rf ./*"
              }
            } else {
              println "${Constants.Colors.RED}Failed to cleanup workspace${Constants.Colors.CLEAR}"
            }
          }
        }
      }
    }
  }
}
