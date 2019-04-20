def jobName = 'AB_Build_Deploy_Server'

String getListTags() {
    """

    import jenkins.model.*

    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
        com.cloudbees.plugins.credentials.Credentials.class,
        Jenkins.instance,
        null,
        null
    )
    def credential = creds.find {it.id == 'ab_bitbucket_cred'}

    if (!credential) {
        return "Unable to pickup credential from Jenkins"
    }

    String l = credential.getUsername()
    String p = credential.getPassword().toString()

    def proc = "git ls-remote https://\$l:\$p@rndwww.nce.amadeus.net/git/scm/abrc/nps.git ML*".execute()
    proc.waitFor()

    proc
        .in
        .text
        .readLines()
        .reverse()
        .subList(0, 5)
        .collect{ it.split()[1].split('/')[-1]}
    """
}

pipelineJob(jobName) {
  description('Build and deploy server part of application')
  parameters {

    activeChoiceParam('git_tag') {
        description('Choose a git tag to build a release')
        choiceType('SINGLE_SELECT')
        groovyScript { script(getListTags()) }
    }

    choiceParam('partner_servers', ['LC7010', 'RDPLC1342',
                                    'LC7010 RDPLC1342'], 'list of Partner servers, use white space to split servers')
    choiceParam('environment',     ['ppt', 'prd'],        'Envireonment to deploy release')
    choiceParam('action',          ['build', 'deploy',
                                    'build deploy'],      '' )
    booleanParam('dry_run',         true,                 "only print command. Don't run")
  }
  definition {
      cps {
        script(readFileFromWorkspace('pipelines/ab_build_deploy_server.groovy'))
      }
  }
}
