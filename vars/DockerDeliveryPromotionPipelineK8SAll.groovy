def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    pipeline {
        agent any
        environment {
            registryURI         = 'registry.hub.docker.com/'

            dev_registry        = 'teamcloudethix/cloudethix-sample-nginx-dev'
            qa_registry         = 'teamcloudethix/cloudethix-sample-nginx-qa'
            stage_registry      = 'teamcloudethix/cloudethix-sample-nginx-stage'
            prod_registry       = 'teamcloudethix/cloudethix-sample-nginx-prod'

            dev_dh_creds        = 'dh_cred_dev'
            qa_dh_creds         = 'dh_cred_qa'
            stage_dh_creds      = 'dh_cred_stage'
            prod_dh_creds       = 'dh_cred_prod'

            COMMITID           = "${params.commit_id}"
        }
        parameters {
            choice(name: 'account', choices: ['dev', 'qa', 'stage', 'prod'], description: 'Select the environment.')
            string(name: 'commit_id', defaultValue: 'latest', description: 'provide commit id.')
        }
        stages {
            stage('Building the Docker Image in Dev') {
                when {
                    expression {
                        params.account == 'dev'
                    }
                }
                environment {
                    dev_registry_endpoint = 'https://' + "${env.registryURI}" + "${env.dev_registry}"
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
                        dev_registry_endpoint = 'https://' + "${env.registryURI}" + "${env.dev_registry}"
                        qa_registry_endpoint  = 'https://' + "${env.registryURI}" + "${env.qa_registry}"
                        dev_image             = "${env.registryURI}" + "${env.dev_registry}" + ':' + "${env.COMMITID}"
                        qa_image              = "${env.registryURI}" + "${env.qa_registry}" + ':' + "${env.COMMITID}"
                    }
                    steps {
                        script {
                            docker.withRegistry(dev_registry_endpoint, dev_dh_creds) {
                                docker.image(dev_image).pull()
                            }

                            sh 'echo Image pulled'

                            sh "docker tag ${env.dev_image} ${env.qa_image}"

                            docker.withRegistry(qa_registry_endpoint , qa_dh_creds) {
                                docker.image(env.qa_image).push()
                            }

                            sh 'echo Image pushed'
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
            stage('Push the Docker Image in stage') {
                when {
                    expression {
                        params.account == 'stage'
                    }
                }
                    environment {
                        qa_registry_endpoint        = 'https://' + "${env.registryURI}" + "${env.qa_registry}"
                        stage_registry_endpoint     = 'https://' + "${env.registryURI}" + "${env.stage_registry}"
                        qa_image                    = "${env.registryURI}" + "${env.qa_registry}" + ':' + "${env.COMMITID}"
                        stage_image                 = "${env.registryURI}" + "${env.stage_registry}" + ':' + "${env.COMMITID}"
                    }
                    steps {
                        script {
                            docker.withRegistry(qa_registry_endpoint, qa_dh_creds) {
                                docker.image(qa_image).pull()
                            }

                            sh 'echo Image pulled'

                            sh "docker tag ${env.qa_image} ${env.stage_image}"

                            docker.withRegistry(stage_registry_endpoint , stage_dh_creds) {
                                docker.image(env.stage_image).push()
                            }

                            sh 'echo Image pushed'
                        }
                    }
                    post {
                        always {
                            sh 'echo Cleaning docker Images from Jenkins.'
                            sh "docker rmi ${env.qa_image}"
                            sh "docker rmi ${env.stage_image}"
                        }
                    }
            }
            stage('Push the Docker Image in prod') {
                when {
                    expression {
                        params.account == 'prod'
                    }
                }
                    environment {
                        stage_registry_endpoint = 'https://' + "${env.registryURI}" + "${env.stage_registry}"
                        prod_registry_endpoint  = 'https://' + "${env.registryURI}" + "${env.prod_registry}"
                        stage_image             = "${env.registryURI}" + "${env.stage_registry}" + ':' + "${env.COMMITID}"
                        prod_image              = "${env.registryURI}" + "${env.prod_registry}" + ':' + "${env.COMMITID}"
                    }
                    steps {
                        script {
                            docker.withRegistry(stage_registry_endpoint, stage_dh_creds) {
                                docker.image(stage_image).pull()
                            }

                            sh 'echo Image pulled'

                            sh "docker tag ${env.stage_image} ${env.prod_image}"

                            docker.withRegistry(prod_registry_endpoint , prod_dh_creds) {
                                docker.image(env.prod_image).push()
                            }

                            sh 'echo Image pushed'
                        }
                    }
                    post {
                        always {
                            sh 'echo Cleaning docker Images from Jenkins.'
                            sh "docker rmi ${env.stage_image}"
                            sh "docker rmi ${env.prod_image}"
                        }
                    }
            }
            stage('Deploy to Dev K8S cluster ') {
                when {
                    expression {
                        params.account == 'dev' //&& env.BRANCH_NAME == 'develop'
                    }
                }
                    environment {
                        kube_config  = "${params.account}" + '-kube-config'
                        account_name = "${params.account}"
                    }
                    steps {
                        echo 'Deploying on Dev K8S Cluster. '
                        deployOnK8s(env.kube_config, env.account_name, env.GIT_COMMIT)
                    }
            }
            stage('Deploy to QA K8S cluster ') {
                when {
                    expression {
                        params.account == 'qa' //&& env.BRANCH_NAME == 'develop'
                    }
                }
                    environment {
                        kube_config  = "${params.account}" + '-kube-config'
                        account_name = "${params.account}"
                    }
                    steps {
                        echo 'Deploying on Dev K8S Cluster. '
                        deployOnK8s(env.kube_config, env.account_name, env.COMMITID)
                    }
            }
            stage('Deploy to STAGE K8S cluster ') {
                when {
                    expression {
                        params.account == 'stage' //&& env.BRANCH_NAME == 'release' || env.BRANCH_NAME == 'hostfix'
                    }
                }
                    environment {
                        kube_config  = "${params.account}" + '-kube-config'
                        account_name = "${params.account}"
                    }
                    steps {
                        echo 'Deploying on Dev K8S Cluster. '
                        deployOnK8s(env.kube_config, env.account_name, env.COMMITID)
                    }
            }
            stage('Deploy to PROD K8S cluster ') {
                when {
                    expression {
                        params.account == 'prod' //&& env.BRANCH_NAME == 'release' || env.BRANCH_NAME == 'hostfix'
                    }
                }
                    environment {
                        kube_config  = "${params.account}" + '-kube-config'
                        account_name = "${params.account}"
                    }
                    steps {
                        echo 'Deploying on Dev K8S Cluster. '
                        deployOnK8s(env.kube_config, env.account_name, env.COMMITID)
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

def deployOnK8s(String KUBE_CONFIG, String ACCOUNT, String COMMIT) {
    withKubeConfig(credentialsId: "${KUBE_CONFIG}", restrictKubeConfigAccess: true) {
                sh "sed -i -e 's/{{ACCOUNT}}/${ACCOUNT}/g' -e 's/{{COMMITID}}/${COMMIT}/g' kube/deployment.yaml"
                sh 'echo deployment.yaml file after replace with sed'
                sh 'cat kube/deployment.yaml'
                sh 'kubectl apply -f kube/deployment.yaml'
                sh 'kubectl apply -f kube/service.yaml'
    }
}
