pipeline {
    agent any

    environment {
        IMAGE_TAG = "${BUILD_NUMBER}"
        DEPLOY_HOST = '192.168.219.100'
        DEPLOY_PATH = '/srv/stone/app/STO'
    }

    triggers {
        pollSCM('H/5 * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Images') {
            steps {
                sh '''
                    docker build -t sto-main:${IMAGE_TAG} ./server/main
                    docker build -t sto-match:${IMAGE_TAG} ./server/match
                    docker build -t sto-batch:${IMAGE_TAG} ./server/batch
                    docker build -t sto-nginx:${IMAGE_TAG} -f ./nginx/Dockerfile .
                '''
            }
        }

        stage('Save Images') {
            steps {
                sh '''
                    rm -rf deploy-images
                    mkdir -p deploy-images

                    docker save sto-main:${IMAGE_TAG} -o deploy-images/sto-main.tar
                    docker save sto-match:${IMAGE_TAG} -o deploy-images/sto-match.tar
                    docker save sto-batch:${IMAGE_TAG} -o deploy-images/sto-batch.tar
                    docker save sto-nginx:${IMAGE_TAG} -o deploy-images/sto-nginx.tar
                '''
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'deploy-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'DEPLOY_USER'
                    )
                ]) {
                    sh '''
                        ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} "
                            mkdir -p ${DEPLOY_PATH}/images
                        "

                        scp -i "$SSH_KEY" -o StrictHostKeyChecking=no deploy-images/*.tar ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/images/
                        scp -i "$SSH_KEY" -o StrictHostKeyChecking=no docker-compose.yml ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}/docker-compose.yml

                        ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} "
                            cd ${DEPLOY_PATH} &&

                            docker load -i images/sto-main.tar &&
                            docker load -i images/sto-match.tar &&
                            docker load -i images/sto-batch.tar &&
                            docker load -i images/sto-nginx.tar &&

                            if grep -q '^IMAGE_TAG=' .env; then
                                sed -i 's/^IMAGE_TAG=.*/IMAGE_TAG=${IMAGE_TAG}/' .env
                            else
                                echo 'IMAGE_TAG=${IMAGE_TAG}' >> .env
                            fi &&

                            docker compose up -d
                        "
                    '''
                }
            }
        }
    }

    post {
        success {
            echo '배포 성공'
        }
        failure {
            echo '배포 실패'
        }
    }
}