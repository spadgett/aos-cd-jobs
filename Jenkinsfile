
properties( [
    [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '360']],
    disableConcurrentBuilds(),
    [$class: 'HudsonNotificationProperty', enabled: false],
    [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    [$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project'],
    pipelineTriggers([[$class: 'TimerTrigger', spec: 'H 11 * * *']])] )

description = ""
failed = false


b = build       job: '../aos-cd-builds/build%2Fopenshift-online', propagate: false,
                        parameters: [
                                        [$class: 'StringParameterValue', name: 'BUILD_MODE', value: 'online:int'],
                                        [$class: 'StringParameterValue', name: 'RELEASE_VERSION', value: '3.6.0'],
                        ]
description += "${b.displayName} - ${b.result}\n"
failed |= (b.result != "SUCCESS")


b = build       job: '../aos-cd-builds/build%2Fopenshift-online', propagate: false,
        parameters: [
                [$class: 'StringParameterValue', name: 'BUILD_MODE', value: 'release'],
                [$class: 'StringParameterValue', name: 'RELEASE_VERSION', value: '3.5.1'],
        ]
description += "${b.displayName} - ${b.result}\n"
failed |= (b.result != "SUCCESS")


currentBuild.description = description.trim()
currentBuild.result = failed?"FAILURE":"SUCCESS"
