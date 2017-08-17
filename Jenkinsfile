#!/usr/bin/env groovy

def mail_success(list,detail) {
    mail(
        to: "${list}",
        from: "aos-cd@redhat.com",
        replyTo: 'jupierce@redhat.com',
        subject: "[aos-devel] Cluster upgrade complete: ${CLUSTER_NAME}",
        body: """\
${detail}

Jenkins job: ${env.BUILD_URL}
""");
}

node('openshift-build-1') {

    properties(
            [[$class              : 'ParametersDefinitionProperty',
              parameterDefinitions:
                      [
                              [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'aos-devel@redhat.com, aos-qe@redhat.com', description: 'Success Mailing List', name: 'MAIL_LIST_SUCCESS'],
                              [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'jupierce@redhat.com, mwoodson@redhat.com', description: 'Success for minor cluster operation', name: 'MAIL_LIST_SUCCESS_MINOR'],
                              [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'jupierce@redhat.com, mwoodson@redhat.com', description: 'Failure Mailing List', name: 'MAIL_LIST_FAILURE'],
                              [$class: 'hudson.model.ChoiceParameterDefinition', choices: "test-key\ncicd\nfree-int\nfree-stg\nstarter-us-east-1\nstarter-us-east-2\nstarter-us-west-1\nstarter-us-west-2", name: 'CLUSTER_NAME', description: 'The name of the cluster to affect'],
                              [$class: 'hudson.model.ChoiceParameterDefinition', choices: "interactive\nquiet\nsilent\nautomatic", name: 'MODE', description: 'Select automatic to prevent input prompt. Select quiet to prevent aos-devel emails. Select silent to prevent any success email.'],
                              [$class: 'hudson.model.StringParameterDefinition', defaultValue: '', description: 'Docker version (e.g. 1.12.6-30.git97ba2c0.el7)', name: 'DOCKER_VERSION'],
                              [$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Mock run to pickup new Jenkins parameters?', name: 'MOCK'],
                      ]
             ]]
    )

    checkout scm

    def deploylib = load( "pipeline-scripts/deploylib.groovy")
    deploylib.initialize()

    currentBuild.displayName = "#${currentBuild.number} - ${CLUSTER_NAME}"

    try {

        sshagent([CLUSTER_NAME]) {

            stage( "pre-check" ) {
                deploylib.run("pre-check")
            }

            if ( MODE != "automatic" ) {
                input "Are you certain you want to =====>UPGRADE<===== the =====>${CLUSTER_NAME}<===== cluster?"
            }

            stage( "pre-upgrade status" ) {
                echo "Cluster status BEFORE upgrade:"
                deploylib.run("status")
            }
            
            stage( "enable maintenance" ) {
                // deploylib.run( "enable-statuspage" )
                deploylib.run( "enable-zabbix-maint" )
                deploylib.run( "disable-config-loop" )
            }

            stage( "upgrade" ) {
                deploylib.run( "upgrade-control-plane", [ "docker_version" : DOCKER_VERSION.trim() ] )
                deploylib.run( "upgrade-nodes", [ "docker_version" : DOCKER_VERSION.trim() ] )
                // deploylib.run( "upgrade-logging" )
                deploylib.run( "upgrade-metrics" )
            }

            stage( "config-loop" ) {
                deploylib.run( "commit-config-loop" )
                deploylib.run( "enable-config-loop" )
                deploylib.run( "run-config-loop" )
            }
            
            stage( "disable maintenance" ) {
                deploylib.run( "disable-zabbix-maint" )
                //deploylib.run( "disable-statuspage" )
            }

            stage( "post-upgrade status" ) {
                POST_STATUS = deploylib.run("status", null, true)
                echo "Cluster status AFTER upgrade:"
                echo POST_STATUS
            }

            stage( "smoketest" ) {
                // run smoketest job here, try{}catch to determine if it failed and  include details of job in success email
            }

            minorUpdate = [ 'test-key', 'cicd' ].contains( CLUSTER_NAME ) || MODE == "quiet"

            if ( MODE != "silent" ) {
                // Replace flow control with: https://jenkins.io/blog/2016/12/19/declarative-pipeline-beta/ when available
                mail_success(minorUpdate?MAIL_LIST_SUCCESS_MINOR:MAIL_LIST_SUCCESS, POST_STATUS)
            }

            stage ( "performance check" ) {
                /* // Disabled until SVT works out issues in their test.
                if ( ( CLUSTER_NAME == "free-int" || CLUSTER_NAME =="test-key" ) && ( OPERATION == "install" || OPERATION == "reinstall" || OPERATION == "upgrade" )  ) {
                    // Run perf1 test on free-int
                    build job: 'cluster%2Fperf',
                        propagate: false,
                        parameters: [
                            [$class: 'hudson.model.StringParameterValue', name: 'CLUSTER_NAME', value: CLUSTER_NAME],
                            [$class: 'hudson.model.StringParameterValue', name: 'OPERATION', value: 'perf1'],
                            [$class: 'hudson.model.StringParameterValue', name: 'MODE', value: 'automatic'],
                        ]
                }
                */
            }

        }

    } catch ( err ) {
        // Replace flow control with: https://jenkins.io/blog/2016/12/19/declarative-pipeline-beta/ when available
        mail(to: "${MAIL_LIST_FAILURE}",
                from: "aos-cd@redhat.com",
                subject: "Error during upgrade on cluster ${CLUSTER_NAME}",
                body: """Encountered an error: ${err}

Jenkins job: ${env.BUILD_URL}
""");
        // Re-throw the error in order to fail the job
        throw err
    }


    if ( MODE != "silent" ) {
        deploylib.send_ci_msg_for_cluster( CLUSTER_NAME )
    }

}
