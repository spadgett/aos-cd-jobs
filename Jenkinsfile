#!/usr/bin/env groovy

// https://issues.jenkins-ci.org/browse/JENKINS-33511
def set_workspace() {
    if(env.WORKSPACE == null) {
        env.WORKSPACE = pwd()
    }
}

// Expose properties for a parameterized build
properties(
        [
                disableConcurrentBuilds(),
                [$class: 'ParametersDefinitionProperty',
                 parameterDefinitions:
                         [
                                 [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'openshift-build-1', description: 'Jenkins agent node', name: 'TARGET_NODE'],
                                 [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'aos-devel@redhat.com, aos-qe@redhat.com', description: 'Success Mailing List', name: 'MAIL_LIST_SUCCESS'],
                                 [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'jupierce@redhat.com,tdawson@redhat.com,smunilla@redhat.com,sedgar@redhat.com,vdinh@redhat.com,ahaile@redhat.com', description: 'Failure Mailing List', name: 'MAIL_LIST_FAILURE'],
                                 [$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Force rebuild even if no changes are detected?', name: 'FORCE_REBUILD'],
                                 [$class: 'hudson.model.ChoiceParameterDefinition',
                                    choices: "online:int\nonline:stg\npre-release\nrelease",
                                    description: '''online:int      online/master -> https://mirror.openshift.com/enterprise/online-openshift-scripts-int/ <br>
                                                    online:stg      online/stage -> https://mirror.openshift.com/enterprise/online-openshift-scripts-stg/ <br>
                                                    pre-release     online/master -> https://mirror.openshift.com/enterprise/online-openshift-scripts/X.Y <br>
                                                    release         online/online-X.Y.Z -> https://mirror.openshift.com/enterprise/online-openshift-scripts/X.Y <br>
                                                    ''',
                                    name: 'BUILD_MODE'],
                                [$class: 'hudson.model.StringParameterDefinition', defaultValue: '3.6.0', description: 'Release version (matches version in branch name for release builds)', name: 'RELEASE_VERSION'],
                                [$class: 'BooleanParameterDefinition', defaultValue: false, description: 'Mock run to pickup new Jenkins parameters?.', name: 'MOCK'],
                         ]
                ],
        ]
)

// Force Jenkins to fail early if this is the first time this job has been run/and or new parameters have not been discovered.
echo "${TARGET_NODE}, MAIL_LIST_SUCCESS:[${MAIL_LIST_SUCCESS}], MAIL_LIST_FAILURE:[${MAIL_LIST_FAILURE}], FORCE_REBUILD:${FORCE_REBUILD}, BUILD_MODE:${BUILD_MODE}"

if ( MOCK.toBoolean() ) {
    error( "Ran in mock mode to pick up any new parameters" )
}

node(TARGET_NODE) {

    set_workspace()
    
    // Login to legacy registry.ops to enable pushes
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'registry-push.ops.openshift.com',
                    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
        sh 'sudo docker login -u $USERNAME -p "$PASSWORD" registry-push.ops.openshift.com'
    }    
    
    // Login to new registry.ops to enable pushes
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'creds_registry.reg-aws',
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
        sh 'oc login -u $USERNAME -p $PASSWORD https://api.reg-aws.openshift.com'

        // Writing the file out is all to avoid displaying the token in the Jenkins console
        writeFile file:"docker_login.sh", text:'''#!/bin/bash
        sudo docker login -u $USERNAME -p $(oc whoami -t) registry.reg-aws.openshift.com:443
        '''
        sh 'chmod +x docker_login.sh'
        sh './docker_login.sh'
    }    
   
    stage('Merge and build') {
        try {
            checkout scm
            env.PATH = "${pwd()}/build-scripts/ose_images:${env.PATH}"
            env.BUILD_MODE = "${BUILD_MODE}"
            env.RELEASE_VERSION = "${RELEASE_VERSION}"
            sshagent(['openshift-bot']) { // merge-and-build must run with the permissions of openshift-bot to succeed
                env.FORCE_REBUILD = "${FORCE_REBUILD}"
                sh "./scripts/merge-and-build-openshift-scripts.sh"
            }
        } catch ( err ) {
            // Replace flow control with: https://jenkins.io/blog/2016/12/19/declarative-pipeline-beta/ when available
            mail(to: "${MAIL_LIST_FAILURE}",
                    from: "aos-cd@redhat.com",
                    subject: "Error building openshift-online",
                    body: """Encoutered an error while running merge-and-build-openshift-scripts.sh: ${err}


Jenkins job: ${env.BUILD_URL}
""");
            // Re-throw the error in order to fail the job
            throw err
        }

    }
}
