def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    pipeline {
        agent any
        environment {
            registryURI         = 'https://registry.hub.docker.com/'

            dev_registry        = 'teamcloudethix/cloudethix-sample-nginx-dev'
            qa_registry         = 'teamcloudethix/cloudethix-sample-nginx-qa'
            stage_registry      = 'teamcloudethix/cloudethix-sample-nginx-stage'
            prod_registry       = 'teamcloudethix/cloudethix-sample-nginx-prod'

            dev_dh_creds        = 'dh_cred_dev'
            qa_dh_creds         = 'dh_cred_qa'
            stage_dh_creds      = 'dh_cred_stage'
            prod_dh_creds       = 'dh_cred_prod'
        }
        parameters {
            choice(name: 'account', choices: ['dev', 'qa', 'stage', 'prod'], description: 'Select the environment.')
        }
        stages {
            stage('Building the Docker Image in Dev') {
                when {
                    expression {
                        params.account == 'dev'
                    }
                }
                environment {
                    dev_registry_endpoint = "${env.registryURI}" + "${env.dev_registry}"
                    dev_image             = "${env.dev_registry}" + ":$GIT_COMMIT"
                }
                steps {
                    script {
                        def app = docker.build(dev_image)
                        docker.withRegistry( dev_registry_endpoint, dev_dh_creds ) {
                            app.push()
                        }
                    }
                }
                post {
                    always {
                        sh 'echo Cleaning docker Images from Jenkins.'
                        sh "docker rmi ${env.dev_image}"
                    }
                }
            }
            stage('Push the Docker Image in QA') {
                when {
                    expression {
                        params.account == 'qa'
                    }
                }
                    environment {
                        dev_registry_endpoint = "${env.registryURI}" + "${env.dev_registry}"
                        qa_registry_endpoint  = "${env.registryURI}" + "${env.qa_registry}"
                        dev_image             = "${env.dev_registry}" + ":$GIT_COMMIT"
                        qa_image              = "${env.qa_registry}" + ":$GIT_COMMIT"
                    }
                    steps {
                        script {
                            docker.withRegistry( dev_registry_endpoint, dev_dh_creds ) {
                                docker.image("${env.dev_image}").pull()
                            }

                            sh "docker tag ${env.dev_image} ${env.qa_image}"

                            docker.withRegistry(qa_registry_endpoint , qa_dh_creds) {
                                docker.image("${env.qa_image}").push()
                            }
                        }
                    }
                    post {
                        always {
                            sh 'echo Cleaning docker Images from Jenkins.'
                            sh "docker rmi ${env.dev_image}"
                            sh "docker rmi ${env.qa_image}"
                        }
                    }
                }
            }
        }
        post {
            always {
                echo 'Deleting Workspace from shared Lib'
                emailext(body: '${DEFAULT_CONTENT}', subject: '${DEFAULT_SUBJECT}', to: '$DEFAULT_RECIPIENTS')
                deleteDir() /* clean up our workspace */
            }
        }
    }
}
