#### Description

Run commands on an agent with a given label, and cleans up the workspace. This
module allows you to specify a timeout, but will default to one hour if not
specified.

#### Optional Parameters

  * label - Label identifying agent to run on.
  * duration - Integer value representing the amount of time.
  * unit - Unit of time. Valid values are: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS.

For more information checkout the [timeout step
documentation](https://jenkins.io/doc/pipeline/steps/workflow-basic-
steps/#code-timeout-code-enforce-time-limit) on
[jenkins.io](https://jenkins.io).

#### Basic Example

    
    
          plNode {
            sh 'printenv'
          }
        

#### Advanced Example

    
    
          plNode 'docker', 5, "MINUTES", {
            docker.image('alpine').inside {
              sh 'printenv'
            }
          }
        

#### Windows Example

    
    
          plNode 'windows', 5, "MINUTES", {
            powershell 'Get-ChildItem env:'
          }
        

#### Node with multiple identifiers Example

    
    
          // Must be an agent with both docker and linux labels on it
          plNode 'linux && docker', 5, "MINUTES", {
            docker.image('alpine').inside {
              sh 'printenv'
            }
          }
        

