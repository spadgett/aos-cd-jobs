
def commonlib = load("pipeline-scripts/commonlib.groovy")

commonlib.initialize()

def initialize() {
}

/**
 * Iterates through a map and flattens it into key1=value1 key2=value2...
 * Appropriate for passing in as tower operation
 * @param map The map to process
 * @return A string of flattened key value properties
 */
@NonCPS // .each has non-serializable aspects, so use NonCPS
def map_to_string(map) {
    def s = ""

    if ( map == null ) {
        return s
    }

    map.each{ k, v -> s += "${k}=${v} " }
    return s
}

def run( operation_name, args = [:], capture_stdout=false ) {
    echo "\n\nRunning operation: ${operation_name} with args: ${args}"
    echo "-------------------------------------------------------"

    output = ""
    waitUntil {
        try {
            cmd = "ssh -o StrictHostKeyChecking=no opsmedic@use-tower2.ops.rhcloud.com ${operation_name} ${this.map_to_string(args)}"
            output = sh(
                    returnStdout: capture_stdout,
                    script: cmd
            )
            return true // finish waitUntil
        } catch ( rerr ) {
            mail(to: "${MAIL_LIST_FAILURE}",
                    from: "aos-cd@redhat.com",
                    subject: "RESUMABLE Error during ${operation_name} on cluster ${CLUSTER_NAME}",
                    body: """Encountered an error: ${rerr}

Input URL: ${env.BUILD_URL}input

Jenkins job: ${env.BUILD_URL}
""");
            def resp = input    message: "On ${CLUSTER_NAME}: Error during ${operation_name} with args: ${args}",
                                parameters: [
                                                [$class: 'hudson.model.ChoiceParameterDefinition',
                                                 choices: "RETRY\nSKIP\nABORT",
                                                 description: 'Retry (try the operation again). Skip (skip the operation without error). Abort (terminate the pipeline).',
                                                 name: 'action']
                                ]

            if ( resp == "RETRY" ) {
                return false  // cause waitUntil to loop again
            } else if ( resp == "SKIP" ) {
                echo "User chose to skip operation: ${operation_name}"
                output = "" //
                return true // Terminate waitUntil
            } else { // ABORT
                error( "User chose to abort job because of error with: ${operation_name}" )
            }
        }
    }

    if ( capture_stdout ) {
        echo "${output}"
        output = output.trim()
    } else {
        output = "capture_stdout=${capture_stdout}"
    }

    echo "-------------------------------------------------------\n\n"

    return output
}

def send_ci_msg_for_cluster(cluster_name) {
    try {
        // Send out a CI message for QE
        build job: 'cluster%2Fsend-ci-msg',
                propagate: false,
                parameters: [
                        [$class: 'hudson.model.StringParameterValue', name: 'CLUSTER_NAME', value: cluster_name],
                ]
    } catch ( err2 ) {
        mail(to: "${MAIL_LIST_FAILURE}",
                from: "aos-cd@redhat.com",
                subject: "Error sending CI msg for cluster ${CLUSTER_NAME}",
                body: """Encountered an error: ${err2}

    Jenkins job: ${env.BUILD_URL}
    """);

    }
}

return this