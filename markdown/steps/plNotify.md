#### Description

Notify a channel in Slack

#### Parameters

  * buildStatus - Should be either STARTED or SUCCESS sets the color of the message in Slack
  * channel - Channel to send message to, can be to a user (via @) or a channel (via #). If sending to a channel the # can be omitted.
  * token - The authentication token for the Slack API (refer to Slack plugin documentation on [how to generate a token](https://github.com/jenkinsci/slack-plugin#install-instructions-for-slack)).
  * domain - The Slack team the token and channel belong to.

#### Example 1

    
    
          plNotify {
            buildStatus = currentBuild.status
            channel = 'auto-team-pipeline'
            token = 'oaiuwjvu908043iounfvou'
            domain = 'concur-test'
          }
        

#### Example 2

If the domain is set in the global config (Manage Jenkins -> Configure System
-> Global Slack Notifier Settings) the domain can be ignored.

    
    
          plNotify {
            buildStatus = currentBuild.status
            channel = 'auto-team-pipeline'
            token = 'oaiuwjvu908043iounfvou'
          }
        

#### Example 3

If you do not have a slack token, you can use Buildhub's default.

    
    
          plNotify {
            buildStatus = currentBuild.status
            channel = 'auto-team-pipeline'
          }
        

