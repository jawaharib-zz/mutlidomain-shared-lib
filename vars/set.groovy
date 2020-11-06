//--------------------------------------------------------------------------------
// getSettings()
//--------------------------------------------------------------------------------
def getSettings() {
    return  [
		deployment : [
			// Maximum number of retries for checking rollout status
			// Status retries are required to address a bug that produces unexpected EOF Errors
			statusRetryMax: 12,
			
			// Time to wait between status retries
			// Status retries are required to address a bug that produces unexpected EOF Errors
			statusRetryTimeout: 75
		],
		accounts : [
			// GitHub account with tag privileges
			githubWrite: 'repo',
			
			// GitHub account with read  privileges
			githubRead: 'repo',

		],
		jenkins: [
			//Owner to use for Jenkins workspace files
			workspaceOwner: 'cicd',

			//Group to use for Jenkins workspace files
			workspaceGroup: 'jenkins',
		],
		// Writable Docker registry connection info for each build or registry promotion environment type.
		// Note that these environments are different than the target deployment environments used for CD
		// since they represent environments associated with build and promotion tasks within the registries.
        writeRegistries: [
            build: [uri: 'console.mu-mds-openshift.tivo.com', credId: 'registry-dev', account: 'slmdockerhosted' ],
            dev: [uri: 'console.mu-mds-openshift.tivo.com', credId: 'registry-dev', account: 'slmdockerhosted' ],
            qa: [uri: 'console.mu-mds-openshift.tivo.com', credId: 'registry-qa', account: 'slmdockertst' ],
            prod: [uri: 'console.mu-mds-openshift.tivo.com', credId: 'registry-prod', account: 'slmdockerprd' ],
        ],
		// Readable Docker registry connection info for each target deployment environment ID.
		// A reference to an OpenShift secret (that defines the user and credential) is defined within the
		// deployment config if authentication is required.  
        readRegistries: [
            dev: [uri: 'console.mu-mds-openshift.tivo.com'],
            deva: [uri: 'console.mu-mds-openshift.tivo.com'],
            devb: [uri: 'console.mu-mds-openshift.tivo.com'],
            qa: [uri: 'console.mu-mds-openshift.tivo.com'],
            qaa: [uri: 'console.mu-mds-openshift.tivo.com'],
            qab: [uri: 'console.mu-mds-openshift.tivo.com'],
            prod: [uri: 'console.mu-mds-openshift.tivo.com'],
            proda: [uri: 'console.mu-mds-openshift.tivo.com'],
            prodb: [uri: 'console.mu-mds-openshift.tivo.com']
        ],
		// Default CI Params to use if they are not overridden
		defaultCIParms : [
			
			STACK_ENV: false,
			BUILD_TAG: 'none',
			DEPLOY_IMAGE: 'none',
			GIT_REPOSITORY: 'https://github.com/tivocorp/mds-oc-cicd.git',
			_APP_NAME: 'acquisition',
			_APP_COMPONENT_NAME: 'service',
			CONFIG_DIR: 'cfg',			
			APP_DIR: '.',		
			BUILD_BRANCH: 'master',						
			NOTIFICATION_RECIPIENT:'',
			NOTIFICATION_CONDITION:'always',
			NOTIFICATION_TYPE:'email'
		]
    ]
}