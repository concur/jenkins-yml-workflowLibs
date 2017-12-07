#!/usr/bin/env groovy
package com.concur

import groovy.transform.Field;
import org.jenkinsci.plugins.github_branch_source.GitHubConfiguration;

@Field def concurPipeline = new com.concur.Commands()
@Field def concurHttp = new com.concur.Http()
@Field def concurUtil = new com.concur.Util()

// Use this if you are making a request to Concur's central GitHub server, endpoint should be anything after api/v3
def githubRequestWrapper(String method, String endpoint, Map postData=null, Map additionalHeaders=null,
                         String credentialsId='', Bool outputResponse=false, Bool ignoreErrors=false) {
  def githubApiUri = getGithubApiUrl()
  endpoint = "${githubApiUri}${endpoint}"
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

@NonCPS
def getGithubApiUrl() {
  def githubEndpoints = new GitHubConfiguration().getEndpoints()
  def endpoint = githubEndpoints.find {
    it.getName() == 'Concur.GitHub.Enterprise'
  }.apiUri
}

/*
 * Get the GitHub Org and Repo
 *
 * String parameter:
 *
 *   @param url -  URL of the GitHub repository
*/
def getGitHubOrgAndRepo(String url = '') {
  if (!url) {
    url = scm.remoteRepositories[0].uris[0].toString()
  }
  def org = ''
  def repo = ''
  if (url.startsWith('https://')) {
    def scmList = new java.net.URI(url).getPath().toString().replaceAll(/\.git|\//,' ').split(' ')
    org = scmList[1]
    repo = scmList[2]
  } else if (url.startsWith('git@')) {
    def scmList = url.replaceAll(/\.git/, '').split(':')[1].split('/')
    org = scmList[0]
    repo = scmList[1]
  } else {
    error("Provided URI is not Git compatible: ${url}")
  }

  return [
    "org": org,
    "repo": repo
  ]
}

def getPullRequests(String org, String repo, String fromBranch='', String baseBranch='', String state='open', String sort='created', String direction='desc') {
  assert org : "Cannot get available pull requests without specifying a GitHub organization"
  assert repo : "Cannot get available pull requests without specifying a GitHub repository"
  String endpoint = "/repos/${org}/${repo}/pulls"
  if (fromBranch) {
    fromBranch = (fromBranch =~ /:/) ? fromBranch : "${org}:${fromBranch}"
    endpoint = concurHttp.addToUriQueryString(endpoint, 'head', fromBranch)
  }
  if (baseBranch) {
    endpoint = concurHttp.addToUriQueryString(endpoint, 'base', baseBranch)
  }
  endpoint = concurHttp.addToUriQueryString(endpoint, 'state', state)
  endpoint = concurHttp.addToUriQueryString(endpoint, 'sort', sort)
  endpoint = concurHttp.addToUriQueryString(endpoint, 'direction', direction)

  def results = githubRequestWrapper('GET', endpoint)
  return concurUtil.parseJSON(results.content)
}

// https://developer.github.com/v3/pulls/#create-a-pull-request
def createPullRequest(String title,
                      String fromBranch,
                      String toBranch,
                      String org,
                      String repo,
                      String summary='Created by Buildhub',
                      Bool maintainer_can_modify=true) {
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
