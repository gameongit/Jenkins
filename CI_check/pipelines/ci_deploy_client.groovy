void runShell(String cmd, Boolean dryRun) {
    dryRun ? echo(cmd.stripIndent()) : sh(cmd.stripIndent())
}

List getSlaveLabels(String environment) {
    Map envLabelsMap =
        [
            'ppt': ['AB_PPT_SRV_1', 'AB_PPT_SRV_2'],
            'prd': ['AB_PRD_SRV_1', 'AB_PRD_SRV_2']
        ]

    return envLabelsMap[environment]
}

String getHostName(String label) {
    hudson.model.Hudson.instance.slaves
  

  
        .toString()
}

String getStatusPage(String hostName) {
    url = "http://${hostName}:8180/rail-client-jnlp-delivery/deployments.xhtml"
    sh(returnStdout: true, script: "curl -s $url")
        .readLines()
        .collect{
            it
                .replaceAll(/<[^>]+>/, '')
                .replaceAll(/\t/, ' ')
                .replaceAll(/\s+$/, '')
        }
        .findAll{ it != '' }
        .join("\n")
}

void downloadZipFile(String zipFile, String zipFilesFolder, Boolean dryRun) {
    runShell("""\
        sudo -u amanps \\
        wget -q http://repository.adp.amadeus.net/repository/master-ops-nsd/1A/NAU/Amadeus-Bahn/jnlp-client/$zipFile \\
        -P $zipFilesFolder\
        """, dryRun)
}

void deploy(zipFilesFolder, zipFile, Boolean dryRun) {
        runShell("sudo -u amanps sh -c 'cd ${zipFilesFolder}; /NPSAPPL/jnlp-server/jnlp-delivery/data/deploy.sh -j $zipFile'", dryRun)
}


String zipFilesFolder = '/NPSAPPL/jnlp-server/jnlp-delivery/data/deploy-input'

Boolean dryRun = dry_run.toBoolean()
currentBuild.displayName = "${BUILD_NUMBER}-${environment.toUpperCase()}"
currentBuild.description = "$clientFinal, $clientSELC"

stage('Download zip files') {
    getSlaveLabels(environment).each { label ->
        [clientFinal, clientSELC].each { zipFile ->
            node(label) {
                downloadZipFile(zipFile, zipFilesFolder, dryRun)
            }
        }
    }
}

stage('Deploy') {
    getSlaveLabels(environment).each { label ->
        [clientFinal, clientSELC].each { zipFile ->
            node(label) {
                deploy(zipFilesFolder, zipFile, dryRun)
            }
        }
    }
}

stage('Status') {
    // We need small timeout to get correct status
    sleep(30)
    getSlaveLabels(environment).each { label ->
        node(label) {
            println("\n Status for host: ${getHostName(label)} \n")
            println getStatusPage(getHostName(label))
        }
    }
}
