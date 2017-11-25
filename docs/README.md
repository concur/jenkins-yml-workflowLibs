# Jenkins YML Workflow

This is a pipeline library for Jenkins 2.x that allows execution to be defined in YML rather than in a Jenkinsfile.

## Requirements

* [Pipeline Shared Groovy Libraries](https://wiki.jenkins.io/display/JENKINS/Pipeline+Shared+Groovy+Libraries+Plugin)
* [Pipeline Remote Loader](https://wiki.jenkins.io/display/JENKINS/Pipeline+Remote+Loader+Plugin)
* [AnsiColor](https://plugins.jenkins.io/ansicolor)

## Usage

This must be added as a global pipeline library in Jenkins.

### Required Environment Variables

| Variable                            | Example                                       | Description                                               |
|-------------------------------------|-----------------------------------------------|-----------------------------------------------------------|
| WORKFLOW_REPOSITORY                 | `git@github.com:reynn/jenkins-workflows.git`  | Points to a repository containing Groovy workflow files.  |
| WORKFLOW_GIT_CREDENTIAL_DESCRIPTION | `GitHub SSH Private Key`                      | A description for checking out the `WORKFLOW_REPOSITORY`. |

## Concepts

### Credential Management

We use lookups for credentials instead of hardcoding credential IDs. This ensures if the Jenkins master has to be rebuilt from scratch jobs will not fail due to the credential no longer existing.

## Contributing

See our [contributing](/docs/CONTRIBUTING.md) guide.
