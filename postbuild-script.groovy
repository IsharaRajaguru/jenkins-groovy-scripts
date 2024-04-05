import java.net.HttpURLConnection
import java.net.URL
import groovy.json.JsonOutput
import hudson.model.*
import hudson.maven.*
import hudson.tasks.*
import hudson.maven.reporters.*
import jenkins.model.Jenkins

def build = manager.build
def envVars = build.getEnvironment(manager.listener)
 
String webHookUrl = envVars['WEB_HOOK_URL']
String jenkinsUrl = envVars['JENKINS_URL']
String project = envVars['JOB_NAME']
String buildStatus = manager.build.@result.toString()
int buildNumber = manager.build.number
 
// Function to send POST request to the webhook URL
def postToTeams(String messageJson, String webHookUrl) {
    try {
        URL url = new URL("${webHookUrl}")
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod("POST")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setDoOutput(true)
        connection.getOutputStream().write(messageJson.getBytes("UTF-8"))
        connection.getOutputStream().flush()
        connection.getOutputStream().close()
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            println("Failed to send message to Teams: HTTP error code : " + connection.getResponseCode())
        } else {
            println("PostBuild Message sent to Teams successfully.")
        }
        connection.disconnect()
    } catch (Exception e) {
        e.printStackTrace()
    }
}
 
def generateMessageJson(webHookUrl, jenkinsUrl, buildStatus, buildNumber, project) {
    def successImage = [
        type: "Image",
        url: "https://knewton-public.s3.amazonaws.com/jenkins-images/jenkins-build-success.png",
        size: "Medium",
        altText: "Jenkins Build Success"
    ]
 
    def failureImage = [
        type: "Image",
        url: "https://knewton-public.s3.amazonaws.com/jenkins-images/jenkins-build-fail.png",
        size: "Medium",
        altText: "Jenkins Build Failure"
    ]
    def buildImage = buildStatus == "SUCCESS" ? successImage : failureImage

    def previousBuildDetails = getPreviousBuildsDetails(project, webHookUrl, jenkinsUrl)
 
    def messageCard = [
        type: "message",
        attachments: [
            [
                contentType: "application/vnd.microsoft.card.adaptive",
                contentUrl: null,
                content: [
                    type: "AdaptiveCard",
		    themeColor: '0072C6',
                    body: [
                        [
                            type: "ColumnSet",
                            columns: [
                                [
                                    type: "Column",
                                    items: [
                                        [
                                            type: "TextBlock",
                                            weight: "Bolder",
                                            text: "Jenkins Build Notification",
                                            wrap: true
                                        ]
                                    ],
                                    width: "auto",
				    verticalContentAlignment: "Center"
                                ],
                                [
                                    type: "Column",
                                    items: [buildImage],
                                    width: "auto"
                                ],
                            ]
                        ],
                        [
                            type: "TextBlock",
                            text: "Latest status of build #${buildNumber}. Click below to view details.",
                            wrap: true
                        ],
                        [
                            type: "FactSet",
                            facts: [
                                [
                                    title: "Project:",
                                    value: project
                                ],
                                [
                                    title: "Status:",
                                    value: buildStatus == "SUCCESS" ? "Build Sucess" : "Build Failue"
                                ],
                                [
                                    title: "Start Time:",
                                    value: previousBuildDetails.lastBuildTime
                                ],
                                [
                                    title: "End Time:",
                                    value: new Date(System.currentTimeMillis()).toString()
                                ],
                                [
                                    title: "Duration:",
                                    value: previousBuildDetails.lastBuildDuration
                                ],
                                [
                                    title: "Last Build Numer:",
                                    value: previousBuildDetails.lastBuildNumer
                                ],
                                [
                                    title: "Last Build Time:",
                                    value: previousBuildDetails.lastBuildTime
                                ],
                                [
                                    title: "Last Success Number:",
                                    value: previousBuildDetails.lastSuccessNumber
                                ],
                                [
                                    title: "Last Success Time:",
                                    value: previousBuildDetails.lastSuccessTime
                                ],
                                [
                                    title: "Last Failed Number:",
                                    value: previousBuildDetails. lastFailedNumber
                                ],
                                [
                                    title: "Last Failed Time:",
                                    value: previousBuildDetails. lastFailedTime
                                ],
                            ],
			    separator: true
                        ]
                    ],
                    actions: [
                        [
                            type: "Action.OpenUrl",
                            title: "View Build Details",
                            url: "${jenkinsUrl}view/alta/job/${project}/${buildNumber}"
                        ]
                    ],
                    $schema: "http://adaptivecards.io/schemas/adaptive-card.json",
                    version: "1.5"
                ]
            ]
        ]
    ]
    return JsonOutput.toJson(messageCard)
}

def getPreviousBuildsDetails(jobName, webHookUrl, jenkinsUrl) {
    def job = Jenkins.instance.getItemByFullName(jobName)

    if (job) {
        def lastBuild = job.getLastBuild()
        def lastSuccessfulBuild = job.getLastSuccessfulBuild()
        def lastStableBuild = job.getLastStableBuild()
        def lastFailedBuild = job.getLastFailedBuild()
        def lastUnsuccessfulBuild = job.getLastUnsuccessfulBuild()

        def lastBuildNumer = lastBuild ? lastBuild.getNumber(): null
        def lastBuildTime = lastBuild ? lastBuild.getTime().toString() : null
        def lastSuccessNumber = lastSuccessfulBuild ? lastSuccessfulBuild.getNumber() : null
        def lastSuccessTime = lastSuccessfulBuild ? lastSuccessfulBuild.getTime().toString() : null
        def lastStableNumber = lastStableBuild ? lastStableBuild.getNumber() : null
        def lastStableTime = lastStableBuild ? lastStableBuild.getTime().toString() : null
        def lastFailedNumber = lastFailedBuild ? lastFailedBuild.getNumber() : null
        def lastFailedTime = lastFailedBuild ? lastFailedBuild.getTime().toString() : null
        def lastUnsuccessfulNumber = lastUnsuccessfulBuild ? lastUnsuccessfulBuild.getNumber() : null
        def lastUnsuccessfulTime = lastUnsuccessfulBuild ? lastUnsuccessfulBuild.getTime().toString() : null
	def lastBuildDuration = covertTime(System.currentTimeMillis() - lastBuild.getTimeInMillis())

        if (lastBuild.getResult().toString() == "SUCCESS") {
            int i = 1
            def failureTime = 0
            if(lastBuild.getNumber()-i == lastFailedBuild.getNumber()){
                failureTime = lastFailedBuild.getTimeInMillis()
                i = i+1
                while((lastBuild.getNumber()-i) >= lastSuccessfulBuild.getNumber()) {
                    Integer number = lastBuild.getNumber() - i
                    def specificBuild = job.getBuildByNumber(number)
                    failureTime = specificBuild.getTimeInMillis()
                    i = i+1
                }
            def failureDuration = System.currentTimeMillis() - failureTime
            sendNotification(jobName, "Back to normal", failureDuration, lastBuild.getNumber(), webHookUrl, jenkinsUrl)
            }
        } else {
            int i=1
            def failureTime = 0
                while((lastBuild.getNumber()-i) >= lastSuccessfulBuild.getNumber()) {
                    Integer number = lastBuild.getNumber() - i
                    def specificBuild = job.getBuildByNumber(number)
                    failureTime = specificBuild.getTimeInMillis()
                    i = i+1
                }
            def failureDuration = System.currentTimeMillis() - failureTime
            sendNotification(jobName, "Failure", failureDuration, lastBuild.getNumber(), webHookUrl, jenkinsUrl)
        }

        return [
            lastBuildNumer: lastBuildNumer,
            lastBuildTime: lastBuildTime,
            lastSuccessNumber: lastSuccessNumber,
            lastSuccessTime: lastSuccessTime,
            lastFailedNumber: lastFailedNumber,
            lastFailedTime: lastFailedTime,
	    lastBuildDuration: lastBuildDuration,
        ]
    } else {
        e.printStackTrace()
        return null
    }
}

def sendNotification(String jobName, String type, long timeMillis, int buildNumber, String webHookUrl, String jenkinsUrl) {
    def message = "${jobName} - #${buildNumber} ${type} after ${covertTime(timeMillis)}"

    def messageCard = [
        type: "message",
        attachments: [
            [
                contentType: "application/vnd.microsoft.card.adaptive",
                contentUrl: null,
                content: [
                    type: "AdaptiveCard",
                    body: [
                        [
                            type: "ColumnSet",
                            columns: [
                                [
                                    type: "Column",
                                    items: [
                                        [
                                            type: "TextBlock",
                                            weight: "Bolder",
                                            text: "Jenkins Build Notification",
                                            wrap: true
                                        ]
                                    ],
                                    width: "auto",
				    verticalContentAlignment: "Center"
                                ]
                            ]
                        ],
                        [
                            type: "TextBlock",
                            text: "Latest status of build #${buildNumber}. Click below to view details.",
                            wrap: true
                        ],
                        [
                            type: "FactSet",
                            facts: [
                                [
                                    title: "Project:",
                                    value: jobName
                                ],
                                [
                                    title: "Details:",
                                    value: message
                                ]
                            ],
			    separator: true
                        ]
                    ],
                    actions: [
                        [
                            type: "Action.OpenUrl",
                            title: "View Build Details",
                            url: "${jenkinsUrl}view/alta/job/${jobName}/${buildNumber}"
                        ]
                    ],
                    $schema: "http://adaptivecards.io/schemas/adaptive-card.json",
                    version: "1.5"
                ]
            ]
        ]
    ]
    def messageJson = JsonOutput.toJson(messageCard)
    postToTeams(messageJson, webHookUrl)
}

def covertTime(long buildTimeMillis) {

    // Convert build time to seconds
    def buildTimeInSeconds = buildTimeMillis / 1000

    // Calculate hours, minutes, and remaining seconds
    def hours = buildTimeInSeconds.intValue() / 3600
    def remainingSeconds = buildTimeInSeconds.intValue() % 3600
    def minutes = remainingSeconds.intValue() / 60
    def seconds = remainingSeconds.intValue() % 60

    if(hours > 0) {
        return "${(int) hours} hr ${(int) minutes} min ${(int) seconds} sec"
    } else if(minutes > 0) {
        return "${(int) minutes} min ${(int) seconds} sec"
    } else {
        return "${(int) seconds} sec"
    }
}
 
unstableRegexes = manager.envVars["UNSTABLE_REGEXES"].tokenize(";")
boolean isInfrastructureError = false
 
if(manager.build.@result == hudson.model.Result.FAILURE) {
    for (regex in unstableRegexes) {
        if(manager.logContains(".*" + regex + ".*")) {
            manager.build.@result = hudson.model.Result.ABORTED
            manager.addWarningBadge("Infrastructure Error")
            isInfrastructureError = true
            buildStatus = "ABORTED"
            break
        }
    }
}
 
// Generate and send the message
String messageJson = generateMessageJson(webHookUrl, jenkinsUrl, buildStatus, buildNumber, project)
postToTeams(messageJson, webHookUrl)
