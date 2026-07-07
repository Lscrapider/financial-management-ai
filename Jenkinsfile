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
        withCredentials([file(credentialsId: 'finance-prod-env-file', variable: 'PROD_ENV_FILE')]) {
          sh '''
            set +x
            cp "$PROD_ENV_FILE" "$ENV_FILE_PATH"
            tr -d '\r' < "$ENV_FILE_PATH" > "$ENV_FILE_PATH.normalized"
            mv "$ENV_FILE_PATH.normalized" "$ENV_FILE_PATH"
            chmod 600 "$ENV_FILE_PATH"
          '''
        }
      }
    }

    stage('Validate Env') {
      steps {
        sh '''
          set +x

          if [ ! -s "$ENV_FILE_PATH" ]; then
            echo "ERROR: $ENV_FILE_PATH is empty. Check Jenkins Secret File credential: finance-prod-env-file"
            exit 1
          fi

          line_count="$(wc -l < "$ENV_FILE_PATH" | tr -d ' ')"
          if [ "$line_count" -lt 20 ]; then
            echo "ERROR: $ENV_FILE_PATH has only $line_count lines. It should be the full multiline .env.github-secret content."
            exit 1
          fi

          required_keys="
          COMPOSE_ENV_FILE
          POSTGRES_HOST
          POSTGRES_PORT
          POSTGRES_DB
          POSTGRES_ADMIN_DATABASE
          POSTGRES_URL
          POSTGRES_USERNAME
          POSTGRES_ADMIN_USER
          POSTGRES_ADMIN_PASSWORD
          POSTGRES_USER
          POSTGRES_PASSWORD
          RABBITMQ_HOST
          RABBITMQ_PORT
          RABBITMQ_MANAGEMENT_PORT
          RABBITMQ_ADMIN_USER
          RABBITMQ_ADMIN_PASSWORD
          RABBITMQ_USERNAME
          RABBITMQ_PASSWORD
          RABBITMQ_VHOST
          INFLUXDB_URL
          INFLUXDB_ORG
          INFLUXDB_BUCKET
          COMMON_INFLUXDB_ADMIN_TOKEN
          INFLUXDB_TOKEN
          MINIO_ENDPOINT
          MINIO_ROOT_USER
          MINIO_ROOT_PASSWORD
          MINIO_USER
          MINIO_PASSWORD
          DEEPSEEK_BASE_URL
          DEEPSEEK_API_KEY
          DASHSCOPE_BASE_URL
          DASHSCOPE_API_KEY
          TUSHARE_BASE_URL
          TUSHARE_TOKEN
          MAIL_HOST
          MAIL_PORT
          MAIL_USERNAME
          MAIL_PASSWORD
          JWT_SECRET
          AGENT_CALLBACK_URL
          AGENT_DATA_GATEWAY_URL
          FINANCE_API_BASE_URL
          BACKEND_PROXY_TARGET
          FRONTEND_PORT
          "

          missing_keys=""
          for key in $required_keys; do
            if ! grep -Eq "^${key}=.+" "$ENV_FILE_PATH"; then
              missing_keys="$missing_keys $key"
            fi
          done

          if [ -n "$missing_keys" ]; then
            echo "ERROR: Required env keys are missing or empty:$missing_keys"
            echo "Fix Jenkins Secret File credential finance-prod-env-file and upload the full .env.github-secret file."
            exit 1
          fi

          echo "Env file validation passed: $line_count lines, required keys are present."
        '''
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

    stage('Build Images') {
      steps {
        sh '''
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" build database-init finance-service finance-python-worker
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" build --no-cache finance-frontend
        '''
      }
    }

    stage('Validate Frontend Image') {
      steps {
        sh '''
          docker run --rm financial-management-ai-finance-frontend:latest \
            sh -c 'test -f /usr/share/nginx/html/finance/index.html \
              && grep -q "src=\\"/finance/" /usr/share/nginx/html/finance/index.html \
              && ! grep -q "%VITE_APP_TITLE%" /usr/share/nginx/html/finance/index.html'
        '''
      }
    }

    stage('Start Services') {
      steps {
        sh '''
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" up -d --force-recreate --remove-orphans
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
