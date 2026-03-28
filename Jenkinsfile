// Spring Boot 서버 통합 파이프라인
// DB_CREATE / SERVER_BUILD / SERVER_RUN 을 독립적으로 선택 실행 가능
pipeline {
    agent any

    parameters {
        booleanParam(name: 'DB_CREATE',     defaultValue: false, description: 'GameDB DROP+CREATE+schema.sql (prod 서버)')
        booleanParam(name: 'SERVER_BUILD',  defaultValue: true,  description: 'Gradle 빌드 + Docker 이미지 빌드 & Push')
        booleanParam(name: 'SERVER_RUN',    defaultValue: true,  description: 'Ubuntu 서버에서 docker compose pull & restart')
    }

    environment {
        IMAGE_NAME  = 'gsyan/sbs'
        DEPLOY_HOST = '192.168.0.61'
        DEPLOY_DIR  = '~/app'
        DB_USER     = 'bk'
        DB_PASS     = '12121212'
        DB_NAME     = 'GameDB'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('DB 재생성') {
            when {
                expression { return params.DB_CREATE }
            }
            steps {
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'DEPLOY_SBS',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    bat """
                        icacls "${SSH_KEY}" /inheritance:r
                        icacls "${SSH_KEY}" /grant:r "NT AUTHORITY\\SYSTEM:(R)"
                        icacls "${SSH_KEY}" /grant:r "BUILTIN\\Administrators:(R)"

                        echo [1/3] DB DROP and CREATE...
                        ssh -o StrictHostKeyChecking=no -i "${SSH_KEY}" ${SSH_USER}@${DEPLOY_HOST} "mysql -u ${DB_USER} -p${DB_PASS} -e 'DROP DATABASE IF EXISTS ${DB_NAME}; CREATE DATABASE ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'"

                        echo [2/3] schema.sql 전송...
                        scp -o StrictHostKeyChecking=no -i "${SSH_KEY}" src\\main\\resources\\sql\\schema.sql ${SSH_USER}@${DEPLOY_HOST}:/tmp/schema.sql

                        echo [3/3] schema.sql 실행...
                        ssh -o StrictHostKeyChecking=no -i "${SSH_KEY}" ${SSH_USER}@${DEPLOY_HOST} "mysql -u ${DB_USER} -p${DB_PASS} ${DB_NAME} < /tmp/schema.sql && rm /tmp/schema.sql"

                        echo DB 재생성 완료!
                    """
                }
            }
        }

        stage('서버 빌드') {
            when {
                expression { return params.SERVER_BUILD }
            }
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

        stage('Docker 빌드 & Push') {
            when {
                expression { return params.SERVER_BUILD }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'DOCKER-HUB',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    bat '''
                        powershell -Command "$Env:DOCKER_PASS | docker login -u %DOCKER_USER% --password-stdin"
                    '''
                    bat "docker build --platform linux/arm64/v8 -t ${IMAGE_NAME}:latest ."
                    bat "docker push ${IMAGE_NAME}:latest"
                }
            }
        }

        stage('서버 재시작') {
            when {
                expression { return params.SERVER_RUN }
            }
            steps {
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'DEPLOY_SBS',
                    keyFileVariable: 'SSH_KEY',
                    usernameVariable: 'SSH_USER'
                )]) {
                    bat """
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
