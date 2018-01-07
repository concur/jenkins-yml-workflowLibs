# com.concur.Http

## addToUriQueryString(String, String, String)

> Adds a key/value to a URL and ensures it is formatted appropriately.

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | uri    |           |
| String | k      |           |
| String | v      |           |

### Example

```groovy
println new com.concur.Http().addToUriQueryString('https://example.com/api', 'a', 'd')
// https://example.com/api?a=d
```

## addMapToQueryString(String, Map)

> Appends the provided Map to the URL with appropriate HTTP formatting

| Type   | Name   | Default   |
|:-------|:-------|:----------|
| String | uri    |           |
| Map    | data   |           |

### Example

```groovy
println new com.concur.Http().addMapToQueryString('https://example.com/api', ['a': 'b', 'c': 'd'])
// https://example.com/api?a=b&c=d
```

## sendSlackMessage(Map)

> Send a slack message. Prior to sending a message there is a check to see if the Slack plugin is installed. Can see more about the parameters for slackSend from [Slack Plugin](https://github.com/jenkinsci/slack-plugin).

__**This does not currently do anything if the plugin is installed.**__

| Type   | Name      | Default   |
|:-------|:----------|:----------|
| Map    | slackData | [:]       |

### Example

```groovy
new com.concur.Http().sendSlackMessage(['channel': 'notifications', 'tokenCredentialId': 'f7136118-359a-4fcb-aba8-a1c6ee7ecb9b', 'message': 'example slack message'])
```