import java.net.HttpURLConnection
import java.net.URL
import java.math.BigDecimal
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
            manager.listener.logger.println("Failed to send message to Teams: HTTP error code : " + connection.getResponseCode())
        } else {
            manager.listener.logger.println("PostBuild Message sent to MS Teams successfully.")
        }
        connection.disconnect()
    } catch (Exception e) {
        manager.listener.logger.println("Error occurred when sending the notifications to MS Teams... Calling postToTeams() function: ${e.getMessage()}")
    }
}

// Analyze the previous build details and call the sendNotification function according to the build statuses.
def analyzeBuildsDetails(String jobName, String webHookUrl, String jenkinsUrl) {
    try {
        def job = Jenkins.instance.getItemByFullName(jobName)

        if (job) {
            def currentBuild = job.getLastBuild()
            def lastSuccessfulBuild = job.getLastSuccessfulBuild()
            def lastFailedBuild = job.getLastFailedBuild()
            def currentBuildNumber = currentBuild.getNumber()
     
            if (currentBuild.getResult().toString() == "SUCCESS") {
                int i = 1
                def failureTime = 0
                if(lastFailedBuild && (currentBuildNumber-i == lastFailedBuild.getNumber())){
                    failureTime = lastFailedBuild.getTimeInMillis()
                    i = i+1
                    while((currentBuildNumber-i) > lastSuccessfulBuild.getNumber()) {
                        Integer buildNumber = currentBuildNumber - i
                        def specificBuild = job.getBuildByNumber(buildNumber)
                        failureTime = specificBuild.getTimeInMillis()
                        i = i+1
                    }
                    def failureDuration = System.currentTimeMillis() - failureTime
                    sendNotification(jobName, "Back to normal", failureDuration, currentBuildNumber, webHookUrl, jenkinsUrl)
                }
            } else {
                int i=1
                def failureTime = 0
                if(lastSuccessfulBuild && (currentBuildNumber-i == lastSuccessfulBuild.getNumber())) {
                    failureTime = currentBuild.getTimeInMillis()
                    def failureDuration = System.currentTimeMillis() - failureTime
                    sendNotification(jobName, "Failure", failureDuration, currentBuildNumber, webHookUrl, jenkinsUrl)
                }
            }
        }
    } catch (Exception e) {
        manager.listener.logger.println("Error occurred when sending the notifications to MS Teams... Calling analyzeBuildsDetails() function: ${e.getMessage()}")
    }
}

// Create message template and post the notifications to MS teams. 
def sendNotification(String jobName, String type, long timeMillis, int buildNumber, String webHookUrl, String jenkinsUrl) {
    try {
        def message = "**${jobName} - #${buildNumber} ${type} after ${convertTime(timeMillis)}**"
        def alertEmoji = '🚨'
     
        def messageCard = [
          "@type": "MessageCard",
          "@context": "https://schema.org/extensions",
          "themeColor": type == 'Back to normal' ? "00FF00": "D70000",
          "summary": "Jenkins Build Notification",
          "sections": [
            [
              "facts": [
                ["name": alertEmoji, "value": message]
              ]
            ]
          ],
          "potentialAction": [
            [
              "@type": "OpenUri",
              "name": "View Build Details",
              "targets": [
                ["os": "default", "uri": "${jenkinsUrl}job/${jobName}/${buildNumber}"]
              ]
            ]
          ]
        ]
        def messageJson = JsonOutput.toJson(messageCard)
        postToTeams(messageJson, webHookUrl)
    } catch (Exception e) {
        manager.listener.logger.println("Error occurred when sending the notifications to MS Teams... Calling sendNotification() function: ${e.getMessage()}")
    }
}

// Function to convert build durations from milliseconds to days, hours, minutes and seconds
def convertTime(long buildTimeMillis) {
    try {
        // Convert build time to seconds with decimals
        BigDecimal buildTimeInSeconds = BigDecimal.valueOf(buildTimeMillis).divide(BigDecimal.valueOf(1000), 1, BigDecimal.ROUND_HALF_UP)
    
        // Calculate days, hours, minutes, and remaining seconds
        def days = buildTimeInSeconds.divide(BigDecimal.valueOf(86400), 0, BigDecimal.ROUND_DOWN)
        def remainingSecondsAfterDays = buildTimeInSeconds.remainder(BigDecimal.valueOf(86400))

        def hours = remainingSecondsAfterDays.divide(BigDecimal.valueOf(3600), 0, BigDecimal.ROUND_DOWN)
        def remainingSecondsAfterHours = remainingSecondsAfterDays.remainder(BigDecimal.valueOf(3600))
        
        def minutes = remainingSecondsAfterHours.divide(BigDecimal.valueOf(60), 0, BigDecimal.ROUND_DOWN)
        def seconds = remainingSecondsAfterHours.remainder(BigDecimal.valueOf(60))
    
        if (days.intValue() > 0) {
            return "${days.intValue()} day ${hours.intValue()} hr"
        } else if(hours.intValue() > 0) {
            return "${hours.intValue()} hr ${minutes.intValue()} min"
        } else if(minutes.intValue() > 0) {
            return "${minutes.intValue()} min ${seconds.setScale(1, BigDecimal.ROUND_HALF_UP)} sec"
        } else {
            return "${seconds.setScale(1, BigDecimal.ROUND_HALF_UP)} sec"
        }
    } catch (Exception e) {
        manager.listener.logger.println("Error occurred when sending the notifications to MS Teams... Calling convertTime() function: ${e.getMessage()}")
    }
}

analyzeBuildsDetails(project, webHookUrl, jenkinsUrl)

