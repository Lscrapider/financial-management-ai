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
    HOST_BUILD_ARTIFACT_DIR = '.ci-artifacts'
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
          DEEPSEEK_MODEL
          DEEPSEEK_API_KEY
          DASHSCOPE_BASE_URL
          DASHSCOPE_MODEL
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

    stage('Validate Host Toolchain') {
      steps {
        sh '''
          set -eu

          missing_tools=""
          for tool in java javac mvn node pnpm python3 docker; do
            if ! command -v "$tool" >/dev/null 2>&1; then
              missing_tools="$missing_tools $tool"
            fi
          done

          if [ -n "$missing_tools" ]; then
            echo "ERROR: Jenkins cannot access required host tools:$missing_tools"
            echo "Install them system-wide or make them accessible to the jenkins service."
            echo "Do not rely on /home/lan/.nvm: the jenkins user cannot access it."
            exit 1
          fi

          if ! python3 -m pip --version >/dev/null 2>&1; then
            echo "ERROR: Python pip is unavailable. Install the Python 3 pip package for the Jenkins host."
            exit 1
          fi

          if ! python3 -m venv --help >/dev/null 2>&1; then
            echo "ERROR: Python venv is unavailable. Install the Python 3 venv package for the Jenkins host."
            exit 1
          fi

          if ! docker compose version >/dev/null 2>&1; then
            echo "ERROR: Docker Compose plugin is unavailable to the jenkins service."
            exit 1
          fi

          java -version
          javac -version
          mvn -version
          node --version
          pnpm --version
          python3 --version
          docker compose version
        '''
      }
    }

    stage('Build Java Artifact') {
      steps {
        dir('backend-java') {
          sh '''
            set -eu

            mvn -q -pl finance-app -am -DskipTests clean package

            jar_count="$(find finance-app/target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' | wc -l | tr -d ' ')"
            if [ "$jar_count" -ne 1 ]; then
              echo "ERROR: Expected exactly one executable JAR in backend-java/finance-app/target, found $jar_count"
              exit 1
            fi
          '''
        }
      }
    }

    stage('Build Python Artifacts') {
      steps {
        sh '''
          set -eu
          set +x

          artifact_root="$HOST_BUILD_ARTIFACT_DIR/python"
          venv_dir="$artifact_root/venv"
          database_wheels="$artifact_root/database-wheels"
          ai_wheels="$artifact_root/ai-wheels"
          model_cache="$artifact_root/ai-huggingface"

          rm -rf "$artifact_root"
          mkdir -p "$database_wheels" "$ai_wheels" "$model_cache"

          python3 -m venv "$venv_dir"
          "$venv_dir/bin/python" -m pip install --upgrade pip
          "$venv_dir/bin/pip" wheel --wheel-dir "$database_wheels" -r database/requirements.txt
          "$venv_dir/bin/pip" wheel --wheel-dir "$ai_wheels" -r ai-python/requirements.txt
          "$venv_dir/bin/pip" install --no-index --find-links="$ai_wheels" -r ai-python/requirements.txt

          read_env_value() {
            key="$1"
            default_value="$2"
            value="$(sed -n "s/^${key}=//p" "$ENV_FILE_PATH" | tail -n 1)"
            if [ -z "$value" ]; then
              value="$default_value"
            fi
            printf '%s' "$value"
          }

          embedding_model_name="$(read_env_value EMBEDDING_MODEL_NAME 'BAAI/bge-base-zh-v1.5')"
          hf_endpoint="$(read_env_value HF_ENDPOINT 'https://hf-mirror.com')"

          HF_HOME="$model_cache" HF_ENDPOINT="$hf_endpoint" EMBEDDING_MODEL_NAME="$embedding_model_name" \
            "$venv_dir/bin/python" -c 'from os import environ; from sentence_transformers import SentenceTransformer; SentenceTransformer(environ["EMBEDDING_MODEL_NAME"])'

          test -n "$(find "$database_wheels" -maxdepth 1 -type f -name '*.whl' -print -quit)"
          test -n "$(find "$ai_wheels" -maxdepth 1 -type f -name '*.whl' -print -quit)"
          test -d "$model_cache"
        '''
      }
    }

    stage('Build Frontend Artifact') {
      steps {
        dir('frontend-vue') {
          sh '''
            set -eu

            export CI=true
            export COREPACK_ENABLE_DOWNLOAD_PROMPT=0
            export VITE_BASE=/finance/
            export VITE_GLOB_CONTEXT_PATH=/finance
            export VITE_GLOB_API_CONTEXT_PATH=/finance-api
            export VITE_COMPRESS=none
            export VITE_PWA=false
            export VITE_ROUTER_HISTORY=hash
            export VITE_INJECT_APP_LOADING=true
            export VITE_ARCHIVER=true
            export VITE_APP_TITLE=理财投资AI知识库
            export VITE_APP_NAMESPACE=vben-web-ele

            pnpm install --frozen-lockfile
            rm -rf apps/web-ele/dist apps/web-ele/dist.zip
            VITE_APP_STORE_SECURE_KEY=please-replace-me-with-your-own-key pnpm -F @vben/web-ele build
            test -f apps/web-ele/dist/index.html
          '''
        }
      }
    }

    stage('Validate Host Build Artifacts') {
      steps {
        sh '''
          set -eu

          jar_count="$(find backend-java/finance-app/target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' | wc -l | tr -d ' ')"
          test "$jar_count" -eq 1
          test -f frontend-vue/apps/web-ele/dist/index.html
          test -d "$HOST_BUILD_ARTIFACT_DIR/python/database-wheels"
          test -d "$HOST_BUILD_ARTIFACT_DIR/python/ai-wheels"
          test -d "$HOST_BUILD_ARTIFACT_DIR/python/ai-huggingface"
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
          docker compose --env-file "$ENV_FILE_PATH" -f "$COMPOSE_FILE_PATH" build database-init finance-service finance-python-worker finance-frontend
        '''
      }
    }

    stage('Validate Frontend Image') {
      steps {
        sh '''
          docker run --rm financial-management-ai-finance-frontend:latest \
            sh -ec '
              INDEX=/usr/share/nginx/html/finance/index.html
              test -f "$INDEX"
              head -c 500 "$INDEX"
              echo
              grep -q "src=\\"/finance/" "$INDEX"
              ! grep -q "%VITE_APP_TITLE%" "$INDEX"
            '
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
