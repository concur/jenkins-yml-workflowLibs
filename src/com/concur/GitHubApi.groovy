#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field;
import org.jenkinsci.plugins.github_branch_source.GitHubConfiguration;

@Field def concurPipeline = new Commands()
@Field def concurHttp     = new Http()
@Field def concurUtil     = new Util()

// Use this if you are making a request to Concur's central GitHub server, endpoint should be anything after api/v3
def githubRequestWrapper(String method, String endpoint, Map postData=null, Map additionalHeaders=null,
                         String credentialsId='', Boolean outputResponse=false, Boolean ignoreErrors=false, String host=null) {
  if (!host) {
    host = new Git().getGitData().host
  }
  // ensure the host doesn't contain a slash at the end
  if (host[-1] == '/') {
    host = host[0..-2]
  }
  endpoint = "$host/$endpoint"

  if (outputResponse == null) {
    outputResponse = concurPipeline.isDebug()
  }

  def validResponseCodes = '100:399'
  if (ignoreErrors) {
    // there are times with the GitHub API in particular where a 404 is acceptable result,
    // this will ensure the httpRequest plugin does not fail the build in for acceptable 404s
    validResponseCodes = '100:599'
  }

  return httpRequest(acceptType: 'APPLICATION_JSON',
                     contentType: 'APPLICATION_JSON',
                     customHeaders: additionalHeaders,
                     url: endpoint,
                     ignoreSslErrors: ignoreErrors,
                     httpMode: method.toUpperCase(),
                     requestBody: groovy.json.JsonOutput.toJson(postData),
                     consoleLogResponseBody: outputResponse,
                     validResponseCodes: validResponseCodes)
}

def githubGraphqlRequestWrapper(String query, Map variables=null, String host=null, String credentialId=null, Boolean outputResponse=false, Boolean ignoreErrors=null) {
  if (!host) {
    def gitDataHost = new Git().getGitData().host
    if (gitDataHost == 'github.com') {
      host = 'https://api.github.com/graphql'
    } else {
      host = "https://$gitDataHost/api/graphql"
    }
  }
  // GraphQL endpoint is static and there is only one so doesn't need to be further variablized.
  if (outputResponse == null) {
    outputResponse = concurPipeline.isDebug()
  }
  def graphQlQuery = ["query": query]
  if (variables) {
    graphQlQuery['variables'] = variables
  }
  concurPipeline.debugPrint('WorkflowLibs :: GitHubApi :: githubGraphqlRequestWrapper', [
    "host"            : host,
    "outputResponse"  : outputResponse,
    "ignoreErrors"    : ignoreErrors,
    "graphQlQuery"    : graphQlQuery])
  
  if (!credentialId) {
    error('workflowLibs :: GitHubApi :: githubGraphqlRequestWrapper :: No credentials provided to authenticate with GitHub, this is required for GraphQL requests.')
  }

  withCredentials([string(credentialsId: credentialId, variable: 'accessToken')]) {
    def headers = ['Authorization': "bearer $accessToken"]
    return httpRequest(acceptType: 'APPLICATION_JSON',
                      contentType: 'APPLICATION_JSON',
                      customHeaders: headers,
                      url: host,
                      ignoreSslErrors: ignoreErrors,
                      httpMode: 'POST',
                      requestBody: groovy.json.JsonOutput.toJson(graphQlQuery),
                      consoleLogResponseBody: outputResponse,
                      validResponseCodes: validResponseCodes)
  }
}

def getPullRequests(String org='', String repo='', String host='', String fromBranch='', String baseBranch='', String state='OPEN', Map credentialData=null) {
  def gitData = new Git().getGitData()
  if (!org) {
    org   = gitData.org
  }

  if (!repo) {
    repo  = gitData.repo
  }

  if (!host) {
    host  = gitData.host
  }

  def query = '''query ($org: String!, $repo: String!, $state:PullRequestState!, $headRef: String, $baseRef: String) {
                   repository(name: $repo, owner: $org) {
                     pullRequests(last: 20, baseRefName: $baseRef, headRefName: $headRef, states: [$state]) {
                       nodes {
                         id
                         number
                         title
                         headRefName
                         baseRefName
                         labels(first: 10) {
                           nodes {
                             name
                           }
                         }
                         mergeable
                       }
                     }
                   }
                 }'''

  def variables = [
    'org'     : org,
    'repo'    : repo,
    'headRef' : fromBranch,
    'baseRef' : baseBranch,
    'state'   : state
  ]

  def results = githubGraphqlRequestWrapper(query, variables, credentialId=null)
  return concurUtil.parseJSON(results.content)?.data?.repository?.pullRequests?.nodes
}

// https://developer.github.com/v3/pulls/#create-a-pull-request
def createPullRequest(String title,
                      String fromBranch,
                      String toBranch,
                      String org,
                      String repo,
                      String summary='Created by Buildhub',
                      Boolean maintainer_can_modify=true) {
  assert org        : "Cannot create a pull request without specifying a GitHub organization."
  assert repo       : "Cannot create a pull request without specifying a GitHub repository."
  assert title      : "Cannot create a pull request without specifying a title."
  assert fromBranch : "Cannot create a pull request without specifying what branch to pull from [fromBranch]."
  assert toBranch   : "Cannot create a pull request without specifying what branch to pull into [toBranch]."

  if (env.CHANGE_FORK) {
    println "Skipping Pull Request creation from a fork. A Pull Request already exists."
    return
  }

  def currentPullRequest = getPullRequests(org, repo, fromBranch, toBranch)
  if (currentPullRequest.any()) {
    concurPipeline.debugPrint(["msg": "PR exists", "data": currentPullRequest ])
    if (currentPullRequest instanceof ArrayList) {
      return currentPullRequest[0]
    } else {
      return currentPullRequest
    }
  }
  def postData = [
    "title"                 : title,
    "body"                  : summary,
    "head"                  : fromBranch,
    "base"                  : toBranch,
    "maintainer_can_modify" : maintainer_can_modify
  ]
  def response = githubRequestWrapper('POST', "/repos/${org}/${repo}/pulls", postData)

  switch (response.status) {
    case 422: println("There is already an existing Pull Request.") // Formula 422 response
      throw javax.ws.rs.core.Response.Status.CONFLICT
    case 404: println "Unable to find Organization/User or repository."
      throw javax.ws.rs.core.Response.Status.NOT_FOUND
    case 201 | 200:
      return concurUtil.parseJSON(response.content)
  }
}
