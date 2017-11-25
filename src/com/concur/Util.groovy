// JSON
def parseJSON(stringContent) {
  assert stringContent : 'Unable to use parseJSON with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  if (utilityStepsAvailable) {
    return readJSON(text: stringContent)
  } else {
    return new groovy.json.JsonSlurperClassic().parseText(stringContent)
  }
}

def toJSON(content) {
  assert content : "Nothing provided to convert to JSON, this should be any Array/List or Map."
  def jsonOutput = groovy.json.JsonOutput.toJson(content)
  return jsonOutput
}

// YAML
def parseYAML(stringContent) {
  assert stringContent : 'Unable to use parseYAML with no content'
  def utilityStepsAvailable = new com.concur.Commands().getPluginVersion('pipeline-utility-steps') ?: false
  if (utilityStepsAvailable) {
    return readYaml(text: stringContent)
  } else {
    @Grab(group='org.yaml', module='snakeyaml', version='1.19')
    return new org.yaml.snakeyaml.Yaml().load(stringContent)
  }
}