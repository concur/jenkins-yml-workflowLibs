# Jenkins YML Workflow

This is a pipeline library for Jenkins 2.x that allows execution to be defined in YML rather than in a Jenkinsfile. Originally developed by the Workflow team at Concur in Bellevue.

## Requirements

### Plugins

* [Pipeline Shared Groovy Libraries](https://wiki.jenkins.io/display/JENKINS/Pipeline+Shared+Groovy+Libraries+Plugin)
* [Pipeline Remote Loader](https://wiki.jenkins.io/display/JENKINS/Pipeline+Remote+Loader+Plugin)
* [Pipeline Utility Steps](https://plugins.jenkins.io/pipeline-utility-steps)
* [AnsiColor](https://plugins.jenkins.io/ansicolor)

## Workflows

Workflows are stored in a separate repo, this allows us to iterate at different speeds and so adoption can be more at will. To view the workflows we provide you can go to the [jenkins-workflows](https://github.com/concur/jenkins-workflows) repository.

## Usage

This must be added as a global pipeline library in Jenkins.

### Required Environment Variables

| Variable                            | Example                                       | Description                                               |
|-------------------------------------|-----------------------------------------------|-----------------------------------------------------------|
| WORKFLOW_REPOSITORY                 | `git@github.com:concur/jenkins-workflows.git` | Points to a repository containing Groovy workflow files.  |
| WORKFLOW_GIT_CREDENTIAL_DESCRIPTION | `GitHub SSH Private Key`                      | A description for checking out the `WORKFLOW_REPOSITORY`. |
| DEFAULT_SLACK_DOMAIN                | `concur`                                      | The Slack team domain to send requests to.                |
| DEFAULT_SLACK_TOKEN_DESC            | `Slack Token`                                 | A credential description for sending to the Slack API     |

## Concepts and Philosophies

### Credential Management

We use lookups for credentials instead of hard-coding credential IDs. This ensures if the Jenkins master has to be rebuilt from scratch jobs will not fail due to the credential no longer existing.

### Workflows (jenkins-workflows) vs WorkflowLibs (jenkins-yml-workflowLibs)

WorkflowLibs are a set of common methods/closures that can be used across any number of workflows. For example this could encapsulate code for publishing artifacts to Artifactory or Nexus, or it could be a method for replacing text in a string.

Workflows are a series of steps for working with tools. We group the steps by overall tool, for instance Glide is a dependency manager for Golang so the steps for interacting with it are in the golang.groovy file.

## Examples

Jenkinsfile:

```groovy
@Library("plWorkflowLibs@v0.5.0")_

plWorkflow {  }
```

pipelines.yml for building a [Golang](https://www.golang.org) project, packaging the result into a [Docker](https://www.docker.com) container and then pushing that image to [Quay.io](https://quay.io).

```yaml
pipelines:
  general:
    debug: true
    dateFormat: "yyyyMMdd-Hmmss"
  tools:
    branches:
      patterns:
        feature: .+
    docker:
      buildArgs:
        CommitSHA: "{{ git_commit }}"
        BuildVersion: "{{ build_version }}"
      credentials:
        description: quay.io robot
      imageName: concur/example-docker-image
      uri: quay.io
    golang:
      buildImage: golang:1.9.2
    slack:
      channel: 'git-notifications'
  branches:
    feature:
      steps:
        - golang:
          - glide:
          - test:
              additionalArgs: "./..."
          - build:
              goEnv:
                GOOS: linux
                GOARCH: amd64
                CGO_ENABLED: 0
              outFile: publish/example
              additionalArgs: "-ldflags \"-X main.buildVersion={{ BUILD_VERSION }} -X main.commit={{ GIT_COMMIT }} -X main.buildDate={{ TIMESTAMP }}\""
              mainPath: cmd/example/main.go
        - docker:
          - build:
          - push:
              additionalTags:
                - "{{ GIT_COMMIT }}"

```

## Contributing

See our [contributing](CONTRIBUTING.md) guide.
