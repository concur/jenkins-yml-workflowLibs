# Jenkins YML Workflow

This is a pipeline library for Jenkins 2.x that allows execution to be defined in YML rather than in a Jenkinsfile. Originally developed by the Workflow team at Concur in Bellevue.

## Requirements

### Plugins

* [Pipeline Shared Groovy Libraries](https://wiki.jenkins.io/display/JENKINS/Pipeline+Shared+Groovy+Libraries+Plugin)
* [Pipeline Remote Loader](https://wiki.jenkins.io/display/JENKINS/Pipeline+Remote+Loader+Plugin)
* [Pipeline Utility Steps](https://plugins.jenkins.io/pipeline-utility-steps)
* [AnsiColor](https://plugins.jenkins.io/ansicolor)

## Usage

This must be added as a global pipeline library in Jenkins.

### Required Environment Variables

| Variable                            | Example                                       | Description                                               |
|-------------------------------------|-----------------------------------------------|-----------------------------------------------------------|
| WORKFLOW_REPOSITORY                 | `git@github.com:reynn/jenkins-workflows.git`  | Points to a repository containing Groovy workflow files.  |
| WORKFLOW_GIT_CREDENTIAL_DESCRIPTION | `GitHub SSH Private Key`                      | A description for checking out the `WORKFLOW_REPOSITORY`. |
| DEFAULT_SLACK_DOMAIN                | `concur`                                      | The Slack team domain to send requests to.                |
| DEFAULT_SLACK_TOKEN_DESC            | `Slack Token`                                 | A credential description for sending to the Slack API     |

## Concepts

### Credential Management

We use lookups for credentials instead of hardcoding credential IDs. This ensures if the Jenkins master has to be rebuilt from scratch jobs will not fail due to the credential no longer existing.

### Workflows (jenkins-workflows) vs WorkflowLibs (jenkins-yml-workflowLibs)

WorkflowLibs are a set of common code snippets that can be used accross many workflows, this could encapsulate code for publishing artifacts to Artifactory or Nexus, or it could be a method for replacing text in a string.

Workflows are a series of steps for working with tools. We group the steps by overall tool, for instance Glide is a dependency manager for Golang so the steps for interacting with it are in the golang.groovy file.

## Contributing

See our [contributing](/docs/CONTRIBUTING.md) guide.
