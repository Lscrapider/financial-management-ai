pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
  }

  environment {
    COMPOSE_FILE_PATH = 'docker/docker-compose.yml'
    ENV_FILE_PATH = '.env'
    DOCKER_NETWORK_NAME = 'database-common-network'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Write Env') {
      steps {
        withCredentials([string(credentialsId: 'finance-prod-env', variable: 'PROD_ENV')]) {
          sh '''
            set +x
            printf '%s\\n' "$PROD_ENV" > "$ENV_FILE_PATH"
            chmod 600 "$ENV_FILE_PATH"
          '''
        }
      }
    }

    stage('Validate Compose') {
      steps {
        sh '''
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" config --quiet
        '''
      }
    }

    stage('Ensure Network') {
      steps {
        sh '''
          docker network inspect "$DOCKER_NETWORK_NAME" >/dev/null 2>&1 \
            || docker network create "$DOCKER_NETWORK_NAME"
        '''
      }
    }

    stage('Deploy') {
      steps {
        sh '''
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" up -d --build --remove-orphans
        '''
      }
    }

    stage('Status') {
      steps {
        sh '''
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" ps
        '''
      }
    }
  }
}
