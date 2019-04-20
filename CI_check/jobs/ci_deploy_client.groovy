def jobName = 'AB_Deploy_Client'

String getListArtifacts(String filter) {
    """
        new URL("http://repository.adp.amadeus.net/repository/master-ops-nsd/1A/NAU/Amadeus-Bahn/jnlp-client/")
            .getText()
            .findAll(/>(JNLP-RailClient-${filter}.*zip)/) {it[1]}
            .reverse()
    """
}

pipelineJob(jobName) {
  description('Deploy client to download servers')
  parameters {
    activeChoiceParam('clientFinal') {
        description('Choose a final type of zip file')
        choiceType('SINGLE_SELECT')
        groovyScript { script(getListArtifacts('Final')) }
    }

    activeChoiceParam('clientSELC') {
        description('Choose a SELC (classic) type of zip file')
        choiceType('SINGLE_SELECT')
        groovyScript { script(getListArtifacts('SELC')) }
    }

    choiceParam('environment',     ['ppt', 'prd'], 'Environment to deploy release')
    booleanParam('dry_run',         true,           "only print command. Don't run")
  }
  definition {
      cps {
        script(readFileFromWorkspace('pipelines/ab_deploy_client.groovy'))
      }
  }
}
