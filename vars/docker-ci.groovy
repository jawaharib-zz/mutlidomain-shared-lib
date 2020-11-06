//--------------------------------------------------------------------------------
// ci
//--------------------------------------------------------------------------------
def call(closure) {
	deployExistingImageId = null
	deployImageId = null
	build = [:]
	closure.resolveStrategy = Closure.DELEGATE_FIRST
	closure.delegate = build
	closure()
	defaultParams = null
	currBuildTag =  null
	bldBranch = null
	bldBranchName = null
	compName = null

	if (build.params == null) {
		defaultParams = set.settings.defaultCIParms
	} else {
		defaultParams = build.params
		
		// Backward Compatibility 
		if ( defaultParams.NOTIFICATION_RECIPIENT == null ) {
			defaultParams.NOTIFICATION_RECIPIENT = ''
		}
		if ( defaultParams.NOTIFICATION_CONDITION == null ) {
			defaultParams.NOTIFICATION_CONDITION = 'failure'
		}
		if ( defaultParams.NOTIFICATION_TYPE == null ) {
			defaultParams.NOTIFICATION_TYPE = 'email'
		}
	}

	//--------------------------------------------------------------------------------
	// Pipeline
	//--------------------------------------------------------------------------------
	pipeline {
		agent none

		environment {
				GIT_SSL_NO_VERIFY = 'true'	
		}	

		parameters {			
			booleanParam(name: 'PROD_CANDIDATE', defaultValue: defaultParams.PROD_CANDIDATE, description: 'Production Candidate')			
			booleanParam(name: 'STACK_ENV', defaultValue: defaultParams.STACK_ENV, description: 'Reuse namespace for branch')
			string(name: 'BUILD_TAG', defaultValue: defaultParams.BUILD_TAG, description: 'Deploy Branch')
			string(name: 'DEPLOY_IMAGE', defaultValue: defaultParams.DEPLOY_IMAGE, description: 'Deploy Image Id')			
			string(name: 'APP_NAME', defaultValue: defaultParams._APP_NAME, description: ' Application Name')
			string(name: 'APP_COMPONENT_NAME', defaultValue: defaultParams._APP_COMPONENT_NAME, description: ' Application Component Name')
			string(name: 'CONFIG_DIR', defaultValue: defaultParams.CONFIG_DIR, description: 'Config directory')
			string(name: 'APP_DIR', defaultValue: defaultParams.APP_DIR, description: 'Application directory')
			string(name: 'BUILD_BRANCH', defaultValue: defaultParams.BUILD_BRANCH, description: 'Deploy Branch')
			string(name: 'GIT_REPOSITORY', defaultValue: defaultParams.GIT_REPOSITORY, description: 'Git Repository')			
			string(name: 'NOTIFICATION_RECIPIENT', defaultValue: defaultParams.NOTIFICATION_RECIPIENT, description: 'Recipient of Notification')				
			string(name: 'NOTIFICATION_CONDITION', defaultValue: defaultParams.NOTIFICATION_CONDITION, description: 'When to trigger always,failure,success')				
			string(name: 'NOTIFICATION_TYPE', defaultValue: defaultParams.NOTIFICATION_TYPE, description: 'type of notification, default is email')				
		}	

		stages {

			//--------------------------------------------------------------------------------		
			// INTIALIZE
			//--------------------------------------------------------------------------------		
			stage('initialize') {
				
				agent { label 'master' }

				steps {
					script {
						echo "Stage: initialize"
						
						echo "BUILD PARAMS: ${params}"

						// --------------------------------------------------------
						// SETTING DYNAMIC PARAMETERS
						// --------------------------------------------------------

						// Get BUILD_BRANCH
						bldBranchName = env.BRANCH_NAME
						bldBranch = bldBranchName.replaceAll("/", "-")
						echo "Setting BUILD_BRANCH to: ${bldBranchName}"
						echo "Setting BUILD_BRANCH_TAG to: ${bldBranch}"
						
						// Do not run for feature and project branch for non-stacking
						if (!params.STACK_ENV) {
							if ( bldBranch =~ /^feature.*/ || bldBranch =~ /^project.*/ ) {
								throw new Exception("Exit pipeline for 'release' and 'project' branch for Non-Stacking Application")
							}
							compName = params._APP_COMPONENT_NAME
						} else {
							// Override Component Name
							echo "Stacking Application"
							compName = params._APP_COMPONENT_NAME + '-' + bldBranch
							echo "Setting _APP_COMPONENT_NAME to: ${compName}"	
						}
					}
				}
			}

			//--------------------------------------------------------------------------------		
			// Create build config
			//--------------------------------------------------------------------------------		
			stage('tag-code') {
				when {
					expression { return 1 }
				}
				agent { label 'master' }

				steps {
					script {
						if( params.DEPLOY_IMAGE == null ||  params.DEPLOY_IMAGE == 'none' || params.DEPLOY_IMAGE == 'null') {
							deployExistingImageId = null
							deployImageId = null
						} else {
							deployExistingImageId =  params.DEPLOY_IMAGE
							deployImageId = params.DEPLOY_IMAGE
						}

						if( params.BUILD_TAG == null ||  params.BUILD_TAG == 'none' || params.BUILD_TAG == 'null') {
							echo "Tag Code"
							currBuildTag  = util.checkoutAndTagBranch(
								params._APP_NAME,
								params._APP_COMPONENT_NAME,
								params.GIT_REPOSITORY,
								set.settings.accounts.githubWrite,
								bldBranchName,
								bldBranch
							)
							echo "Setting BUILD_TAG to: ${params.BUILD_TAG}"
						} else {
							currBuildTag = params.BUILD_TAG
						}
					}
				}
			}

			//--------------------------------------------------------------------------------		
			// Validate Code
			//--------------------------------------------------------------------------------		
			stage('validate-code') {
				when {
					expression {  deployExistingImageId == null  }
				}

				agent { label 'master' }

				steps {
					script {
						echo "Checkout Code"
						util.checkoutTag(params.GIT_REPOSITORY, currBuildTag)
						echo "Verify Code"
					}
				}
			}


			//--------------------------------------------------------------------------------		
			// Create build config
			//--------------------------------------------------------------------------------		
			stage('build-image') {
				when {
					expression { deployExistingImageId == null }
				}

				agent { label 'master' }

				steps {
					script { 
						connConfig = null
						script {
							echo "Configure OpenShift Build config"   // Need to see its useful or not
							
							withCredentials([string(credentialsId: 'build', variable: 'token')]) {					
								buildRequest = [
									_APP_NAME: params._APP_NAME,
									_APP_COMPONENT_NAME: compName,
									GIT_REPOSITORY: params.GIT_REPOSITORY,
									DOCKER_REGISTRY: params.DOCKER_REGISTRY,
									CONFIG_DIR: params.CONFIG_DIR,
									BUILD_TAG: currBuildTag,
									TOKEN: token,
									APP_DIR: params.APP_DIR
								]
								
								echo "Checkout Code"
								util.checkoutTag(params.GIT_REPOSITORY, currBuildTag)

								//echo "Create OpenShift Build Config"	
								

								//echo "Run OpenShift Build"
								deployImageId = util.runBuild(buildRequest)
								echo "Build Image ID: ${deployImageId}"
								echo "Build TAG: ${currBuildTag}"
								
								echo "Build Finished"

								echo "Extract image locally"
								util.extractImageLocal(params._APP_NAME, compName, params.APP_DIR, deployImageId)		
							}							
						}
					}
				}
			}


			//--------------------------------------------------------------------------------		
			// Validate Image
			//--------------------------------------------------------------------------------		
			stage('validate-image') {
				when {
					expression {  deployExistingImageId == null  }
				}

				agent { label 'master' }

				steps {
					script {
						echo "Checkout Code"
						util.checkoutTag(params.GIT_REPOSITORY, currBuildTag)
						echo "Verify Image"
					}
				}
			}


			//--------------------------------------------------------------------------------		
			// Run unit tests
			//--------------------------------------------------------------------------------		
			stage('unit-test') {
				when {
					expression {  deployExistingImageId == null  }
				}

				agent { label 'master' }

				steps {
					script {
						echo "unitTest"
						if (build.testScriptUnitTest != null) {
							build.testScriptUnitTest()
						}						
					}
				}
			}

			
			//--------------------------------------------------------------------------------		
			// Run integration tests
			//--------------------------------------------------------------------------------		
			stage('integration-test') {
				when {
					expression {  deployExistingImageId == null  }
				}

				agent { label 'master' }

				steps {
					script {
						echo "integrationTest"
						if (build.testScriptIntegrationTest != null) {
							build.testScriptIntegrationTest()
						}						
					}
				}
			}

			//--------------------------------------------------------------------------------		
			// Set Build Description
			//--------------------------------------------------------------------------------
			stage('set-build-description') {
				when {
					expression { return 1 }
				}
				agent { label 'master' }

				steps {
					script {
						echo "Set Build Description"
						util.setBldDescription(
							params.GIT_REPOSITORY,
							params._APP_NAME,
							params._APP_COMPONENT_NAME,
							currBuildTag,
							deployImageId
						)
					}
				}
			}		
			
		}

		//--------------------------------------------------------------------------------
		// Post Step
		//--------------------------------------------------------------------------------
		post {
        	always {
				script {
					echo "Post Step - Sending email "
					echo "Recipient: ${params.NOTIFICATION_RECIPIENT}"
					echo "Notification Condition: ${params.NOTIFICATION_CONDITION}"
					echo "Notification Type: ${params.NOTIFICATION_TYPE}"
					util.sendNotification(params.NOTIFICATION_RECIPIENT, params.NOTIFICATION_TYPE, params.NOTIFICATION_CONDITION)
				}
			}
    	}
	}
	return this	
}