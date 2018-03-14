#### Description

Checkout from Github and set environment variables

#### Parameters

  * repo - Github repository to checkout if you need to get something other than the project repo (optional)
  * credentials - ID of the credentials to use for this checkout, if not supplied uses checkout credentials defined in the job (optional)
  * withSubmodules - If true will checkout the configured git repository including any configured submodules (optional)

#### Example

    
    
          plGitCheckout { } // will checkout the project configured by this job
        

#### Submodule example

    
    
          plGitCheckout { // will checkout the project configured by this job
            withSubmodules = true
          }
        

#### Specific repo example

    
    
          def credentialId = concurPipeline.getCredentialsWithCriteria(['description': 'Primary GitHub clone/checkout credentials'])
          plGitCheckout { // will checkout the project configured by this job
            credentials = credentialId
            repo = 'https://github.com/ansible/ansible.git'
          }
        

