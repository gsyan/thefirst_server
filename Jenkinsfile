// Spring Boot 서버 빌드 파이프라인 (빌드 + Docker 이미지 Push + Ubuntu 서버 배포)
pipeline {
    agent any

    environment {
        IMAGE_NAME = 'gsyan/sbs'
        DEPLOY_HOST = '192.168.0.61'
        DEPLOY_DIR  = '~/app'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                withCredentials([file(credentialsId: 'FIREBASE_SERVICE_ACCOUNT', variable: 'SERVICE_ACCOUNT_FILE')]) {
                    bat '''
                        echo Copying Firebase service account file...
                        copy "%SERVICE_ACCOUNT_FILE%" src\\main\\resources\\firebase-service-account.json

                        echo Building project...
                        gradlew.bat clean build --no-daemon
                    '''
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'DOCKER-HUB',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    bat '''
                        echo Logging in using PowerShell...
                        powershell -Command "$Env:DOCKER_PASS | docker login -u %DOCKER_USER% --password-stdin"
                    '''
                    bat "docker build --platform linux/arm64/v8 -t ${IMAGE_NAME}:latest ."
                    bat "docker push ${IMAGE_NAME}:latest"
                }
            }
        }

        stage('Deploy to Ubuntu Server') {
            steps {
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'DEPLOY_SBS',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    bat """
                        echo ========================================
                        echo   SSH 배포 시작 (Ubuntu 서버, Docker Compose)
                        echo   사용자: ${SSH_USER}
                        echo   키 경로: ${SSH_KEY}
                        echo ========================================

                        icacls "${SSH_KEY}" /inheritance:r
                        icacls "${SSH_KEY}" /grant:r "NT AUTHORITY\\SYSTEM:(R)"
                        icacls "${SSH_KEY}" /grant:r "BUILTIN\\Administrators:(R)"

                        ssh -o StrictHostKeyChecking=no -i "${SSH_KEY}" ${SSH_USER}@${DEPLOY_HOST} "cd ${DEPLOY_DIR} && docker compose pull sbs-app && docker compose up -d --remove-orphans && echo '배포 성공!'"
                    """
                }
            }
        }
    }

    post {
        failure {
            echo '빌드 실패. 각 스테이지 로그 확인'
        }
    }
}
