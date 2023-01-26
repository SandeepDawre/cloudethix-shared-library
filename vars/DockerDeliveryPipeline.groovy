pipeline {
    agent any
    stages {
        stage('Example') {
            steps {
                getPlatformName()
            }
        }
    }
}

def getPlatformName() {
  
  return config.platform
}
