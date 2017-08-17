#!/usr/bin/env groovy

// Expose properties for a parameterized build
properties(
        [
            [$class : 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '720']],
            [$class : 'ParametersDefinitionProperty',
          parameterDefinitions:
                  [
                          [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'openshift-build-1', description: 'Jenkins agent node', name: 'TARGET_NODE'],
                          [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'git@github.com:jupierce', description: 'Github base for repos', name: 'GITHUB_BASE'],
                          [$class: 'hudson.model.ChoiceParameterDefinition', choices: "openshift-bot\naos-cd-test", defaultValue: 'aos-cd-test', description: 'SSH credential id to use', name: 'SSH_KEY_ID'],
                          [$class: 'hudson.model.ChoiceParameterDefinition', choices: "3.7\n3.6\n3.5\n3.4\n3.3", defaultValue: '3.7', description: 'OCP Version to build', name: 'BUILD_VERSION'],
                          [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'aos-devel@redhat.com, aos-qe@redhat.com,jupierce@redhat.com,smunilla@redhat.com,ahaile@redhat.com', description: 'Success Mailing List', name: 'MAIL_LIST_SUCCESS'],
                          [$class: 'hudson.model.StringParameterDefinition', defaultValue: 'jupierce@redhat.com,smunilla@redhat.com,ahaile@redhat.com', description: 'Failure Mailing List', name: 'MAIL_LIST_FAILURE'],
                          [$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Enable intra-day build hack for CL team CI?', name: 'EARLY_LATEST_HACK'],
                          [$class: 'hudson.model.ChoiceParameterDefinition', choices: "release\npre-release\nonline:int\nonline:stg", description:
'''
release                   {ose,origin-web-console,openshift-ansible}/release-X.Y ->  https://mirror.openshift.com/enterprise/enterprise-X.Y/latest/<br>
pre-release               {origin,origin-web-console,openshift-ansible}/release-X.Y ->  https://mirror.openshift.com/enterprise/enterprise-X.Y/latest/<br>
online:int                {origin,origin-web-console,openshift-ansible}/master -> online-int yum repo<br>
online:stg                {origin,origin-web-console,openshift-ansible}/stage -> online-stg yum repo<br>
''', name: 'BUILD_MODE'],
                          [$class: 'hudson.model.BooleanParameterDefinition', defaultValue: true, description: 'Sign RPMs with openshifthosted?', name: 'SIGN'],
                          [$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Mock run to pickup new Jenkins parameters?', name: 'MOCK'],
                          [$class: 'hudson.model.BooleanParameterDefinition', defaultValue: false, description: 'Run as much code as possible without pushing / building?', name: 'TEST'],
                          [$class: 'hudson.model.TextParameterDefinition', defaultValue: "", description: 'Include special notes in the build email?', name: 'SPECIAL_NOTES'],
                  ]
            ],
            disableConcurrentBuilds()
        ]
)

IS_TEST_MODE = TEST.toBoolean()
BUILD_VERSION_MAJOR = BUILD_VERSION.tokenize('.')[0].toInteger() // Store the "X" in X.Y
BUILD_VERSION_MINOR = BUILD_VERSION.tokenize('.')[1].toInteger() // Store the "Y" in X.Y
SIGN_RPMS = SIGN.toBoolean()

def mail_success( version ) {

    def target = "(Release Candidate)"
    def mirrorURL = "https://mirror.openshift.com/enterprise/enterprise-${version.substring(0,3)}"

    if ( BUILD_MODE == "online:int" ) {
        target = "(Integration Testing)"
        mirrorURL = "https://mirror.openshift.com/enterprise/online-int"
    }

    if ( BUILD_MODE == "online:stg" ) {
        target = "(Stage Testing)"
        mirrorURL = "https://mirror.openshift.com/enterprise/online-stg"
    }

    def inject_notes = ""
    if ( SPECIAL_NOTES.trim() != "" ) {
        inject_notes = "\n***Special notes associated with this build****\n${SPECIAL_NOTES.trim()}\n***********************************************\n"
    }

    mail(
            to: "${MAIL_LIST_SUCCESS}",
            from: "aos-cd@redhat.com",
            replyTo: 'smunilla@redhat.com',
            subject: "[aos-devel] New build for OpenShift ${target}: ${version}",
            body: """\
OpenShift Version: v${version}
${inject_notes}
Puddle (internal): http://download-node-02.eng.bos.redhat.com/rcm-guest/puddles/RHAOS/AtomicOpenShift/${version.substring(0,3)}/${OCP_PUDDLE}
  - Mirror: ${mirrorURL}/${OCP_PUDDLE}
  - Images have been built for this puddle
  - Images have been pushed to registry.reg-aws.openshift.com:443
  - Images have been pushed to registry.ops

Brew:
  - Openshift: ${OSE_BREW_URL}
  - OpenShift Ansible: ${OA_BREW_URL}

Jenkins job: ${env.BUILD_URL}

${OSE_CHANGELOG}
""");
}


node(TARGET_NODE) {

    checkout scm
    AOS_CD_JOBS_COMMIT_SHA = sh(
            returnStdout: true,
            script: "git rev-parse HEAD",
    ).trim()

    PUDDLE_CONF_BASE="https://raw.githubusercontent.com/openshift/aos-cd-jobs/${AOS_CD_JOBS_COMMIT_SHA}/build-scripts/puddle-conf"
    if ( SIGN_RPMS ) {
        PUDDLE_CONF="${PUDDLE_CONF_BASE}/atomic_openshift-${BUILD_VERSION}_signed.conf"

    } else {
        PUDDLE_CONF="${PUDDLE_CONF_BASE}/atomic_openshift-${BUILD_VERSION}.conf"
    }

    def commonlib = load( "pipeline-scripts/commonlib.groovy")
    commonlib.initialize()

    try {
        sshagent([SSH_KEY_ID]) { // To work on real repos, buildlib operations must run with the permissions of openshift-bot

            def buildlib = load( "pipeline-scripts/buildlib.groovy")
            buildlib.initialize()
            echo "Initializing build: #${currentBuild.number} - ${BUILD_VERSION}.?? (${BUILD_MODE})"

            stage( "ose repo" ) {
                master_spec = buildlib.initialize_ose()
            }

            stage( "origin-web-console repo" ) {
                sh "go get github.com/jteeuwen/go-bindata"
                buildlib.initialize_origin_web_console()
                dir( WEB_CONSOLE_DIR ) {
                    // Enable fake merge driver used in our .gitattributes
                    sh "git config merge.ours.driver true"
                    // Use fake merge driver on specific directories
                    // We will be re-generating the dist directory, so ignore it for the merge
                    sh "echo 'dist/** merge=ours' >> .gitattributes"
                }
            }

            stage( "openshift-ansible repo" ) {
                buildlib.initialize_openshift_ansible()
            }

            stage( "analyze" ) {

                dir ( env.OSE_DIR ) {

                    // If the target version resides in ose#master
                    IS_SOURCE_IN_MASTER = ( BUILD_VERSION == master_spec.major_minor )

                    if ( IS_SOURCE_IN_MASTER ) {
                        if ( BUILD_MODE == "release" ) {
                            error( "You cannot build a release while it resides in master; cut an enterprise branch" )
                        }
                    } else {
                        if ( BUILD_MODE != "release" && BUILD_MODE != "pre-release" ) {
                            error( "Invlaid build mode for a releaes that does not reside in master: ${BUILD_MODE}" )
                        }
                    }

                    if ( IS_SOURCE_IN_MASTER ) {
                        OSE_SOURCE_BRANCH = "master"
                        UPSTREAM_SOURCE_BRANCH = "upstream/master"
                    } else {
                        OSE_SOURCE_BRANCH = "enterprise-${BUILD_VERSION}"
                        if ( BUILD_MODE == "release" ) {
                            // When building in release mode, no longer pull from upstream
                            UPSTREAM_SOURCE_BRANCH = null
                        } else {
                            UPSTREAM_SOURCE_BRANCH = "upstream/release-${BUILD_VERSION}"
                        }
                        // Create the non-master source branch and have it track the origin ose repo
                        sh "git checkout -b ${OSE_SOURCE_BRANCH} origin/${OSE_SOURCE_BRANCH}"
                    }

                    echo "Building from ose branch: ${OSE_SOURCE_BRANCH}"

                    spec = buildlib.read_spec_info("origin.spec")
                    rel_fields = spec.release.tokenize(".")

                    if ( BUILD_MODE == "online:int" || BUILD_MODE == "online:stg") {
                        /**
                         * In non-release candidates, we need the following fields
                         *      REL.INT.STG
                         * REL = 0    means pre-release,  1 means release
                         * INT = fields used to differentiate online:int builds
                         * STG = fields used to differentiate online:stg builds
                         */

                        while ( rel_fields.size() < 3 ) {
                            rel_fields << "0"    // Ensure there are enough fields in the array
                        }

                        if ( rel_fields[0].toInteger() != 0 ) {
                            // It's technically possible to do this, but why?
                            error( "Do not build released products in ${BUILD_MODE}; just build in release or pre-release mode" )
                        }

                        if ( rel_fields.size() != 3 ) { // Did we start with > 3? That's too weird to continue
                            error( "Unexpected number of fields in release: ${spec.release}" )
                        }

                        if ( BUILD_MODE == "online:int" ) {
                            rel_fields[1] = rel_fields[1].toInteger() + 1  // Bump the INT version
                            rel_fields[2] = 0  // If we are bumping the INT field, everything following is reset to zero
                        }

                        if ( BUILD_MODE == "online:stg" ) {
                            rel_fields[2] = rel_fields[2].toInteger() + 1  // Bump the STG version
                        }

                        NEW_RELEASE = "${rel_fields[0]}.${rel_fields[1]}.${rel_fields[2]}"

                    } else if ( BUILD_MODE == "release" || BUILD_MODE == "pre-release" ) {
                        NEW_RELEASE = "${rel_fields[0].toInteger() + 1}"  // Only keep the first field, and increment it
                    } else {
                        error( "Unknown BUILD_MODE: ${BUILD_MODE}" )
                    }

                    currentBuild.displayName = "#${currentBuild.number} - ${spec.version}-${NEW_RELEASE} (${BUILD_MODE})"

                }
            }

            stage( "prep web-console" ) {
                dir( WEB_CONSOLE_DIR ) {
                    // Unless building for stage, origin-web-console#entperise-X.Y should be used
                    if ( BUILD_MODE == "online:stg" ) {
                        WEB_CONSOLE_BRANCH = "stage"
                        sh "git checkout -b stage origin/stage"
                    } else {
                        WEB_CONSOLE_BRANCH = "enterprise-${spec.major_minor}"
                        sh "git checkout -b ${WEB_CONSOLE_BRANCH} origin/${WEB_CONSOLE_BRANCH}"
                        if ( IS_SOURCE_IN_MASTER ) {
                            sh """
                                # Pull content of master into enterprise branch
                                git merge master --no-commit --no-ff
                                # Use grunt to rebuild everything in the dist directory
                                ./hack/install-deps.sh
                                grunt build

                                git add dist
                                git commit -m "Merge master into enterprise-${BUILD_VERSION}" --allow-empty
                            """

                            if ( ! IS_TEST_MODE ) {
                                sh "git push"
                            }

                            // Clean up any unstaged changes (e.g. .gitattributes)
                            sh "git reset --hard HEAD"
                        }
                    }
                }
            }

            stage( "merge origin" ) {
                dir( OSE_DIR ) {
                    // Enable fake merge driver used in our .gitattributes
                    sh "git config merge.ours.driver true"
                    // Use fake merge driver on specific packages
                    sh "echo 'pkg/assets/bindata.go merge=ours' >> .gitattributes"
                    sh "echo 'pkg/assets/java/bindata.go merge=ours' >> .gitattributes"

                    if ( UPSTREAM_SOURCE_BRANCH != null ) {
                        // Merge upstream origin code into the ose branch
                        sh "git merge -m 'Merge remote-tracking branch ${UPSTREAM_SOURCE_BRANCH}' ${UPSTREAM_SOURCE_BRANCH}"
                    } else {
                        echo "No origin upstream in this build"
                    }
                }
            }

            stage( "merge web-console" ) {
                dir( OSE_DIR ) {

                    // Vendor a particular branch of the web console into our ose branch and capture the SHA we vendored in
                    // TODO: Is this necessary? If we don't specify a GIT_REF, will it just use the current branch
                    // we already setup?
                    // TODO: Easier way to get the VC_COMMIT by just using parse-rev when we checkout the desired web console branch?
                    VC_COMMIT = sh(
                            returnStdout: true,
                            script: "GIT_REF=${WEB_CONSOLE_BRANCH} hack/vendor-console.sh 2>/dev/null | grep 'Vendoring origin-web-console' | awk '{print \$4}'",
                    ).trim()

                    // Vendoring the console will rebuild this assets, so add them to the ose commit
                    sh """
                        git add pkg/assets/bindata.go
                        git add pkg/assets/java/bindata.go
                    """
                }
            }

            stage( "ose tag" ) {
                dir( OSE_DIR ) {
                    // Set the new release value in the file and tell tito to keep the version & release in the spec.
                    buildlib.set_rpm_spec_release_prefix( "origin.spec", NEW_RELEASE )
                    // Note that I did not use --use-release because it did not maintain variables like %{?dist}
                    sh "tito tag --accept-auto-changelog --keep-version --debug"
                    if ( ! IS_TEST_MODE ) {
                        sh "git push"
                        sh "git push --tags"
                    }
                    OSE_CHANGELOG = buildlib.read_changelog( "origin.spec" )
                }
            }

            stage( "openshift-ansible prep" ) {
                dir( OPENSHIFT_ANSIBLE_DIR ) {
                    if ( BUILD_MODE == "online:stg" ) {
                        sh "git checkout -b stage origin/stage"
                    } else {
                        if ( ! IS_SOURCE_IN_MASTER ) {
                            // At 3.6, openshift-ansible switched from release-1.X to match 3.X release branches
                            if ( BUILD_VERSION_MAJOR == 3 && BUILD_VERSION_MINOR < 6 ) {
                                sh "git checkout -b release-1.${BUILD_VERSION_MINOR} origin/release-1.${BUILD_VERSION_MINOR}"
                            } else {
                                sh "git checkout -b release-${BUILD_VERSION} origin/release-${BUILD_VERSION}"
                            }
                        } else {
                           sh "git checkout master"
                        }
                    }
                }
            }

            stage( "openshift-ansible tag" ) {
                dir(OPENSHIFT_ANSIBLE_DIR) {
                    if ( BUILD_VERSION_MAJOR == 3 && BUILD_VERSION_MINOR < 6 ) {
                        // Use legacy versioning if < 3.6
                        sh "tito tag --debug --accept-auto-changelog"
                    } else {
                        // If >= 3.6, keep openshift-ansible in sync with OCP version
                        buildlib.set_rpm_spec_version( "openshift-ansible.spec", spec.version )
                        buildlib.set_rpm_spec_release_prefix( "openshift-ansible.spec", NEW_RELEASE )
                        // Note that I did not use --use-release because it did not maintain variables like %{?dist}
                        sh "tito tag --debug --accept-auto-changelog --keep-version --debug"
                    }

                    if ( ! IS_TEST_MODE ) {
                        sh "git push"
                        sh "git push --tags"
                    }
                    OA_CHANGELOG = buildlib.read_changelog( "openshift-ansible.spec" )
                }
            }

            if ( IS_TEST_MODE ) {
                error( "This is as far as the test process can proceed without triggering builds" )
            }

            stage( "rpm builds" ) {

                // Allow both brew builds to run at the same time

                dir( OSE_DIR ) {
                    OSE_TASK_ID = sh(   returnStdout: true,
                            script: "tito release --debug --yes --test aos-${BUILD_VERSION} | grep 'Created task:' | awk '{print \$3}'"
                    )
                    OSE_BREW_URL = "https://brewweb.engineering.redhat.com/brew/taskinfo?taskID=${OSE_TASK_ID}"
                    echo "ose rpm brew task: ${OSE_BREW_URL}"
                }
                dir( OPENSHIFT_ANSIBLE_DIR ) {
                    OA_TASK_ID = sh(   returnStdout: true,
                            script: "tito release --debug --yes --test aos-${BUILD_VERSION} | grep 'Created task:' | awk '{print \$3}'"
                    )
                    OA_BREW_URL = "https://brewweb.engineering.redhat.com/brew/taskinfo?taskID=${OA_TASK_ID}"
                    echo "openshift-ansible rpm brew task: ${OA_BREW_URL}"
                }

                // Watch the tasks to make sure they succeed. If one fails, make sure the user knows which one by providing the correct brew URL
                try {
                    sh "brew watch-task ${OSE_TASK_ID}"
                } catch ( ose_err ) {
                    echo "Error in ose build task: ${OSE_BREW_URL}"
                    throw ose_err
                }
                try {
                    sh "brew watch-task ${OA_TASK_ID}"
                } catch ( oa_err ) {
                    echo "Error in openshift-ansible build task: ${OA_BREW_URL}"
                    throw oa_err
                }
            }

            stage( "signing rpms" ) {
                if ( SIGN_RPMS ) {
                    sh "${env.WORKSPACE}/build-scripts/sign_rpms.sh rhaos-${OSE_VERSION}-rhel-7 openshifthosted"
                } else {
                    echo "RPM signing has been skipped..."
                }
            }

            stage( "puddle: ose 'building'" ) {
                buildlib.build_puddle(
                        PUDDLE_CONF,    // The puddle configuration file to use
                        "-b",   // do not fail if we are missing dependencies
                        "-d",   // print debug information
                        "-n",   // do not send an email for this puddle
                        "-s",   // do not create a "latest" link since this puddle is for building images
                        "--label=building"   // create a symlink named "building" for the puddle
                )
            }

            stage( "compare dist-git" ) {
                sh "ose_images.sh --user ocp-build compare_nodocker --branch rhaos-${BUILD_VERSION}-rhel-7 --group base"
            }

            stage( "update dist-git" ) {
                sh "ose_images.sh --user ocp-build update_docker --branch rhaos-${BUILD_VERSION}-rhel-7 --group base --force --release '${NEW_RELEASE}.0' --version 'v${spec.version}'"
            }

            stage( "build images" ) {
                // TODO: Create a dynamic .repo file pointing to the exact puddle we built instead of "building" so that we can run X.Y builds in parallel
                sh "ose_images.sh --user ocp-build build_container --branch rhaos-${BUILD_VERSION}-rhel-7 --group base --repo http://download.lab.bos.redhat.com/rcm-guest/puddles/RHAOS/repos/aos-unsigned-building.repo"
            }

            if ( EARLY_LATEST_HACK.toBoolean() ) {
                // Hack to keep from breaking openshift-ansible CI during US Eastern daylight builds. They need the latest puddle to exist
                // before images are pushed to registry-ops in order for their current CI implementation to work.
                OCP_PUDDLE = buildlib.build_puddle(
                        PUDDLE_CONF,
                        "-b", // do not fail if we are missing dependencies
                        "-d", // prin debug information
                        "-n" // do not send an email for the puddle
                )
            }

            stage( "push images" ) {
                dir( "${env.WORKSPACE}/build-scripts/ose_images" ) {
                    TAG_LATEST = IS_SOURCE_IN_MASTER?"":"--nolatest"
                    sh "sudo ./ose_images.sh --user ocp-build push_images ${TAG_LATEST} --branch rhaos-${BUILD_VERSION}-rhel-7 --group base"
                    sh "sudo ./ose_images.sh --user ocp-build push_images ${TAG_LATEST} --branch rhaos-${BUILD_VERSION}-rhel-7 --group base --push_reg registry.reg-aws.openshift.com:443"
                }
            }

            if ( ! EARLY_LATEST_HACK.toBoolean() ) {
                // If we have not done so already, create the "latest" puddle
                OCP_PUDDLE = buildlib.build_puddle(
                        PUDDLE_CONF,
                        "-b", // do not fail if we are missing dependencies
                        "-d", // prin debug information
                        "-n" // do not send an email for the puddle
                )
            }

            echo "Created puddle on rcm-guest: /mnt/rcm-guest/puddles/RHAOS/AtomicOpenShift/${BUILD_VERSION}/${OCP_PUDDLE}"

            // Push the latest puddle out to the correct directory on the mirrors (e.g. online-int, online-stg, or enterprise-X.Y)
            buildlib.invoke_on_rcm_guest( "push-to-mirrors.sh", "simple", BUILD_VERSION, BUILD_MODE )

            buildlib.invoke_on_rcm_guest( "publish-oc-binary.sh", BUILD_VERSION, VERSION )

            echo "Finished building OCP ${VERSION}-${NEW_RELEASE}"


            mail_success( spec.version )
        }
    } catch ( err ) {  
        mail(to: "${MAIL_LIST_FAILURE}",
                from: "aos-cd@redhat.com",
                subject: "Error building OSE: ${BUILD_VERSION}",
                body: """Encountered an error while running OCP pipeline: ${err}
    
    Jenkins job: ${env.BUILD_URL}
    """);
        throw err
    }


}
