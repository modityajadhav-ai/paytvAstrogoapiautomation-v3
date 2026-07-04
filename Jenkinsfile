pipeline {
    agent any

    tools {
        maven 'Maven3'
        jdk 'JDK17'
    }

    // Daily at 7:00 AM server local time. Adjust cron to match your timezone.
    triggers {
        cron('0 7 * * *')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('API tests') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn -B -ntp clean test -Dallure.report.skip=true'
                    } else {
                        bat 'mvn -B -ntp clean test -Dallure.report.skip=true'
                    }
                }
            }
        }

        stage('Archive Allure') {
            steps {
                archiveArtifacts artifacts: 'target/allure-results/**', allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/extent-reports/*.html,target/excel-reports/*.xlsx', allowEmptyArchive: true
        }
        success {
            emailext(
                subject: "[API Tests] PASSED — Build #${env.BUILD_NUMBER}",
                body: '''Daily API regression completed successfully.

Extent report and Excel summary are attached.
View build: ${BUILD_URL}''',
                to: '${DEFAULT_RECIPIENTS}',
                attachmentsPattern: 'target/extent-reports/*.html,target/excel-reports/*.xlsx'
            )
        }
        failure {
            emailext(
                subject: "[API Tests] FAILED — Build #${env.BUILD_NUMBER}",
                body: '''Daily API regression failed.

See attached reports and console log.
View build: ${BUILD_URL}''',
                to: '${DEFAULT_RECIPIENTS}',
                attachmentsPattern: 'target/extent-reports/*.html,target/excel-reports/*.xlsx'
            )
        }
    }
}
