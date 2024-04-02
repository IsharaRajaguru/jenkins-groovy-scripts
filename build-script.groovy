import java.net.HttpURLConnection
import java.net.URL
import groovy.json.JsonOutput

def postToTeams(String messageJson) {
    try {
      String webHookUrl = System.getenv('WEB_HOOK_URL')
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
            println("Message sent to Teams successfully.")
        }
        
        connection.disconnect()
    } catch (Exception e) {
        e.printStackTrace()
    }
}

def generateMessageJson(buildNumber, project, branch) 
    
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
                                ],
				[
                                    type: "Column",
                                    items: [
                                        [
                                            type: "Image",
                                            url: "https://knewton-public.s3.amazonaws.com/jenkins-images/jenkins-build-inprogress.png",
                                            size: "Medium",
                                            altText: "Jenkins Build In Progress"
                                        ]
                                    ],
                                    width: "auto"
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
                                    	value: project
                                ],
                                [
                                	title: "Status:",
                                	value: "Build Started"
                                ]
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


String jenkinsUrl = System.getenv('JENKINS')
String project = System.getenv('JOB_NAME')
String branch = System.getenv('BRANCH_NAME')
String buildNumber = System.getenv('BUILD_NUMBER') ?: 'Unknown'

String messageJson = generateMessageJson(buildNumber, project, branch, jenkinsUrl)
postToTeams(messageJson)
