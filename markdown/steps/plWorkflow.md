#### Description

Run a workflow based on the YAML structure defined. All of these are optional,
and you can add as many as you need.

#### Parameters

  * nodeType - The type of machine to build on, should be Windows or Linux (Optional: Default linux).
  * notify - Whether or not to send notifications at the beginning and end of the pipeline (Optional: Default true)
  * yamlPath - The relative path to the YAML file used to describe your pipeline (Optional: Default pipelines.yml)
  * repo - Used to specify a GitHub repository for GitHub deployments when on a deployhub server. (Optional: Default inferred from your project
  * githubDeployments - Whether or not to send the deployment status to GitHub. This is only relevant for deploy hubs. (Optional: Default true)
  * notify - Whether or not to send a slack notification for the start and completion of the build (Optional: Default true)
  * useSubmodules - Used to clone submodules if they exist in your repository (Optional: Default true)
  * timeoutDuration - Set the timeout duration for the build (Optional: Default 1)
  * timeoutUnit - Set the Unit for the timeout duration (Optional: Default 'HOURS')
  * yamlText - Used to troubleshoot pipelines.yml (Optional: Default null)

#### Slack Notification Example

    
    
          plWorkflow {
            notify = false
          }
        

#### YAML Path and Node Type Example

    
    
          plWorkflow {
            yamlPath = 'data.yml'
            nodeType = 'windows'
          }
        

