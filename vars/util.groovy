//--------------------------------------------------------------------------------
// checkoutAndTagBranch(tring appName, String compName, String repo, String account, String branch)
//--------------------------------------------------------------------------------
def checkoutAndTagBranch(String appName, String compName, String repo, String account, String branch, String branchName) {

    def tagName = "${appName}-${compName}-${branchName}-${env.BUILD_NUMBER}"

    git(branch: branch, credentialsId: 'repo', url: repo)
	sh("git tag -f -m \"${tagName}\" $tagName")
    
    withCredentials([string(credentialsId: 'bitbucket-tag', variable: 'GITPASS')]) {					
        sh('echo \"echo $GITPASS\" > gitpass.sh')
        sh("chmod u+x gitpass.sh")
		repoWithUser = repo.replaceFirst('https://', "https://${account}@")
        sh("git -c core.askPass=./gitpass.sh push --force ${repoWithUser} ${tagName}")
    }
	return tagName	
}

//--------------------------------------------------------------------------------
// checkoutTag(String repo, String tagName)
//--------------------------------------------------------------------------------
def checkoutTag(String repo, String tagName) {
	def branchName = "refs/tags/${tagName}"
    checkout([$class: 'GitSCM',
		branches: [[name: branchName]],
		doGenerateSubmoduleConfigurations: false,
		extensions: [],
		submoduleCfg: [],
		userRemoteConfigs: [[credentialsId: 'repo', url: repo]],
	])
}

//--------------------------------------------------------------------------------
// sendNotification(String sendTo, type)
//--------------------------------------------------------------------------------
def sendNotification(String sendTo, String type, String condition) {	
	if (sendTo.length() > 0)
	{
		if (condition.equalsIgnoreCase('always')) {
			sendNotification(sendTo, type)
		}
		else {			
			if (currentBuild.currentResult.equalsIgnoreCase(condition)) {
				sendNotification(sendTo, type)
			}
		}
	}
}

//--------------------------------------------------------------------------------
// sendNotification(String sendTo, type)
//--------------------------------------------------------------------------------
def sendNotification(String sendTo, String type) {	
	if (type.equalsIgnoreCase('email')) {
		notifyEmail(sendTo)
	}	
}

//--------------------------------------------------------------------------------
// notifyEmail(String sendTo)
//--------------------------------------------------------------------------------
def notifyEmail(String mailTo) {
	// check to see if the email is valid format
	try {
		emailext body:"<h1>${currentBuild.currentResult}</h1><br>Project: ${env.JOB_NAME} <br>Build Number: ${env.BUILD_NUMBER} <br> Build URL: ${env.BUILD_URL}",
                		mimeType: 'text/html', 
                		to: "${mailTo}",
                		subject: "[JENKINS] Job ${env.JOB_NAME} - ${currentBuild.currentResult}"	
	}
	catch (err) {
		echo "${err}"
		echo "${err.toString()}"
		throw err
	}

}

//--------------------------------------------------------------------------------
// runBuild(def buildRequest)
//--------------------------------------------------------------------------------
def runBuild(Map buildRequest) {
	openshift.withCluster("insecure://ocp-console.${buildRequest.BUILD_HOSTING_CLUSTER}", buildRequest.TOKEN) {
		def fullAppName ="${buildRequest._APP_NAME}-${buildRequest._APP_COMPONENT_NAME}"
		openshift.withProject(buildRequest.BUILD_HOSTING_PROJECT) {						
			echo "Building Tag: ${buildRequest.BUILD_TAG}"
			def res = openshift.startBuild("${fullAppName}", "--wait=true", "--commit='${buildRequest.BUILD_TAG}'")

			echo "Build Completed for: ${fullAppName}"					
			def bc = openshift.selector('bc', "${fullAppName}")
			bc.logs()
			
			def digest = res.object().status.output.to.imageDigest
			def imageId="@${digest}"

			echo "Build Image ID: ${imageId}"
			echo "Finished"
			return imageId
		}
	}	
}

//--------------------------------------------------------------------------------
// extractImageLocal(String appName, String compName, String appDir, String imageId)
//--------------------------------------------------------------------------------
def extractImageLocal(String appName, String compName, String appDir, String imageId) {
	withCredentials([string(credentialsId: set.settings.writeRegistries['dev'].credId, variable: 'pw')]) {

		def account = set.settings.writeRegistries['dev'].account
		def registry = set.settings.writeRegistries['dev'].uri
		def wsOwner = set.settings.jenkins.workspaceOwner
		def wsGroup = set.settings.jenkins.workspaceGroup
        def srcImage = "${registry}/${appName}/${compName}${imageId}"
		def targetDir = "${appDir}/tmp/extract"

		sh("dzdo docker login --username ${account} --password ${pw} ${registry}")

		echo "Pulling Container Image: ${srcImage}"
		sh("dzdo docker pull ${srcImage}")
		
		tmpContainerName = sh(returnStdout: true, script: "dzdo docker create ${srcImage}")
		tmpContainerName = tmpContainerName.replace("\n", "")
		echo "Creating Temp Container Name: ${tmpContainerName}"

		echo "Creating empty image extraction directory: ${targetDir}"
		sh("rm -fr ${targetDir}")
		sh("mkdir -p ${targetDir}")

		echo "Extracting docker container image to directory: ${targetDir}"
		sh("( dzdo docker cp ${tmpContainerName}:/ - ) | tar -C ${targetDir} --owner=${wsOwner} --group=${wsGroup} -xvf - ")

		echo "Fixing permissions in directory: ${targetDir}"
		sh("chown -R ${wsOwner} ${targetDir}")
		sh("chgrp -R ${wsGroup} ${targetDir}")
		sh("chmod -R u+rw ${targetDir}")

		echo "Listing contents of directory: ${targetDir}"
		sh("find ${targetDir}")
		sh("dzdo docker rm ${tmpContainerName}")		
	}
}