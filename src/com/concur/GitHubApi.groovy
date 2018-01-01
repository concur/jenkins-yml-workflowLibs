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

  def debugMode = concurPipeline.isDebug()

  if (outputResponse == null) {
    outputResponse = debugMode
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
                    quiet: debugMode,
                    requestBody: groovy.json.JsonOutput.toJson(postData),
                    consoleLogResponseBody: outputResponse,
                    validResponseCodes: validResponseCodes)
}

def githubGraphqlRequestWrapper(String query, Map variables=null, String host=null, String credentialId=null, Boolean outputResponse=false, Boolean ignoreSslErrors=false) {
  if (!host) {
    def gitDataHost = new Git().getGitData().host
    if (gitDataHost == 'github.com') {
      host = 'https://api.github.com/graphql'
    } else {
      host = "https://$gitDataHost/api/graphql"
    }
  }
  def debugMode = concurPipeline.isDebug()
  // GraphQL endpoint is static and there is only one so doesn't need to be further variablized.
  if (outputResponse == null) {
    outputResponse = debugMode
  }
  def graphQlQuery = ["query": query]
  if (variables) {
    graphQlQuery['variables'] = variables
  }
  concurPipeline.debugPrint('WorkflowLibs :: GitHubApi :: githubGraphqlRequestWrapper', [
    "host"            : host,
    "outputResponse"  : outputResponse,
    "ignoreSslErrors" : ignoreSslErrors,
    "graphQlQuery"    : graphQlQuery])
  
  if (!credentialId) {
    error('workflowLibs :: GitHubApi :: githubGraphqlRequestWrapper :: No credentials provided to authenticate with GitHub, this is required for GraphQL requests.')
  }

  withCredentials([string(credentialsId: credentialId, variable: 'accessToken')]) {
    def headers = [['name':'Authorization', 'value': "bearer $accessToken"]]
    return httpRequest(acceptType: 'APPLICATION_JSON',
                      contentType: 'APPLICATION_JSON',
                      customHeaders: headers,
                      url: host,
                      ignoreSslErrors: ignoreSslErrors,
                      httpMode: 'POST',
                      quiet: debugMode,
                      requestBody: groovy.json.JsonOutput.toJson(graphQlQuery),
                      consoleLogResponseBody: outputResponse)
  }
}

def getPullRequests(String credentialId, String owner='', String repo='', String host='', String fromBranch='', String baseBranch='', String state='OPEN') {
  def gitData = new Git().getGitData()
  if (!owner) {
    owner = gitData.org
  }

  if (!repo) {
    repo = gitData.repo
  }

  def query = '''query ($owner: String!, $repo: String!, $state:PullRequestState!, $headRef: String, $baseRef: String) {
                   repository(name: $repo, owner: $owner) {
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

  Map variables = [
    'owner'   : owner,
    'repo'    : repo,
    'state'   : state
  ]
  if (fromBranch) {
    variables['headRef'] = fromBranch
  }
  if (baseBranch) {
    variables['baseRef'] = baseBranch
  }

  concurPipeline.debugPrint([
    'query'     : query,
    'variables' : variables,
    'host'      : host
  ])

  def results = githubGraphqlRequestWrapper(query, variables, host, credentialId)
  return concurUtil.parseJSON(results.content)?.data?.repository?.pullRequests?.nodes
}

def getReleases(Map credentialData, String owner='', String repo='', String host='', int limit=10) {
  def gitData = new Git().getGitData()
  if (!owner) {
    owner = gitData.org
  }

  if (!repo) {
    repo = gitData.repo
  }

  def query = '''query ($repo: String!, $owner: String!, $limit: Int!) {
                  repository(name: $repo, owner: $owner) {
                    releases(last: $limit) {
                      nodes {
                        tag {
                          name
                          target {
                            oid
                          }
                        }
                        createdAt
                        isPrerelease
                        name
                      }
                    }
                  }
                }'''

  Map variables = [
    'owner'   : owner,
    'repo'    : repo,
    'limit'   : limit
  ]

  def credentialId = concurPipeline.getCredentialsWithCriteria(credentialData).id

  def results = githubGraphqlRequestWrapper(query, variables, host, credentialId)
  return concurUtil.parseJSON(results.content)?.data?.repository?.releases?.nodes
}

// https://developer.github.com/v3/pulls/#create-a-pull-request
def createPullRequest(String title,
                      String fromBranch,
                      String toBranch,
                      String owner,
                      String repo,
                      String host,
                      Map credentialData,
                      String summary="Automatically created at ${env.BUILD_URL}",
                      Boolean maintainer_can_modify=true) {
  assert owner      : "Cannot create a pull request without specifying a GitHub organization."
  assert repo       : "Cannot create a pull request without specifying a GitHub repository."
  assert title      : "Cannot create a pull request without specifying a title."
  assert fromBranch : "Cannot create a pull request without specifying what branch to pull from [fromBranch]."
  assert toBranch   : "Cannot create a pull request without specifying what branch to pull into [toBranch]."

  if (env.CHANGE_FORK) {
    println "Skipping Pull Request creation from a fork. A Pull Request already exists."
    return
  }
  def credentialId = concurPipeline.getCredentialsWithCriteria(credentialData).id

  def currentPullRequest = getPullRequests(credentialId, owner, repo, host, fromBranch, toBranch)
  if (currentPullRequest.any()) {
    println """workflowLibs :: GitHubApi :: createPullRequest :: A pull request already exists for the branches specified:
              |---------------------------------
              |Title              : ${currentPullRequest.title}
              |PR Number          : ${currentPullRequest.number}
              |Destination Branch : ${currentPullRequest.baseRefName}
              |From Branch        : ${currentPullRequest.headRefName}""".stripMargin()
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
  
// def githubRequestWrapper(String method, String endpoint, Map postData=null, Map additionalHeaders=null,
//                          String credentialsId='', Boolean outputResponse=false, Boolean ignoreErrors=false, String host=null) {
  def response = githubRequestWrapper('POST', "/repos/$owner/$repo/pulls", postData, null, credentialId, false, false, host)
  concurPipeline.debugPrint([
    'response.statusCode' : response?.statusCode,
    'response.content'    : response?.content
  ])
  return concurUtil.parseJSON(response?.content)
}
