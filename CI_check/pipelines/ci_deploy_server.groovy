String getSlaveLabel(String environment) {
    Map envLabelsMap =
        [
            'ppt': 'AB_BUILD',
            'prd': 'AB_SRV_PROD'
        ]

    return envLabelsMap[environment]
}

void runShell(String cmd, Boolean dryRun) {
    dryRun ? echo(cmd.stripIndent()) : sh(cmd.stripIndent())
}

List getCredentials(String env) {
    Map envCredentialsName = [
        'ppt'   : 'ab_weblogic_ppt_password',
        'prd'   : 'ab_weblogic_prd_password'
    ]

    [
        string(credentialsId: 'ab_ora_pass', variable: 'oraclePass'),
        string(credentialsId: envCredentialsName[env], variable: 'webLogicPass')
    ]
}

void buildServer(String gitTag, String partnerServer, List targetServers, String webLogicPass, String oraclePass, Boolean dryRun) {
    runShell("""\
        sudo -u nvsbuild sh -c '
        cd ~/build &&
        ./nvsrelease_auto.sh -v ${gitTag} -t ${targetServers[0]} -d $partnerServer -u amanps \\
        -r true -l ${targetServers.join(',')} -p $webLogicPass -o $oraclePass'
        """, dryRun)
}

List getTargetServers(environment) {
    Map envPartnerServerMap =
        [
            'ppt': ['mucvnvsppt1', 'mucvnvsppt2', 'mucvnvsppt3', 'mucvnvsppt4'],
            'prd': ['mucvnpsprd1', 'mucvnpsprd2', 'mucvnpsprd3', 'mucvnpsprd4']
        ]

    return envPartnerServerMap[environment]
}

void startPartnerServer(partnerServer, Boolean dryRun) {
    runShell("""\
        sudo -u amanps sh -c '
        cd /NPSAPPL/Oracle/Middleware/user_projects/domains/$partnerServer/bin &&
        ./startNPS.sh start'
        """, dryRun)
}


void deployServer(String gitTag, String partnerServer, List targetServers, Boolean dryRun) {
    runShell("""\
        sudo -iu amanps sh -c '\
        cd /NPSAPPL/Oracle/Middleware/user_projects/domains/ ;
        rm -rf OLD.$partnerServer ;
        ./npsinstall_auto.sh -d $partnerServer -j ${targetServers[0]}-amanps-${partnerServer}-${gitTag}.jar -r ${targetServers.join(',')}'
        """, dryRun)
}

void getStatus(String environment, Boolean dryRun) {
    node(getSlaveLabel(environment)) {
        runShell("sudo -iu amanps sh -c '/NPSAPPL/Oracle/Middleware/user_projects/domains/service_th -st'", dryRun)
    }
}

Boolean dryRun = dry_run.toBoolean()

currentBuild.displayName = "${BUILD_NUMBER}-${git_tag}-${environment.toUpperCase()}"
currentBuild.description = "partner=${partner_servers} options=$action"

List partnerServerList = partner_servers.split().collect{"partnerServer" + it}

timestamps {
    stage('Build') {
        if (action.contains('build')) {
            node(getSlaveLabel('ppt')) {
                partnerServerList.each { partnerServer ->
                    withCredentials(getCredentials(environment)) {
                        buildServer(git_tag, partnerServer, getTargetServers(environment), webLogicPass, oraclePass, dryRun)
                    }
                }
            }
        }
    }

    stage('Deploy') {
        if (action.contains('deploy')) {
            partnerServerList.each { partnerServer ->
                node(getSlaveLabel(environment)) {
                    deployServer(git_tag, partnerServer, getTargetServers(environment), dryRun)
                    startPartnerServer(partnerServer, dryRun)
                }
            }
        }
    }
    stage('Status') {
        if (action.contains('deploy')) {
            getStatus(environment, dryRun)
        }
    }
}
