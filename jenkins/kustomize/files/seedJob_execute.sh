#!/bin/bash

FILE=$JENKINS_HOME/api_token.txt

JENKINS_URL=http://127.0.0.1:8080
JENKINS_USER=admin

launch_seed_job() {

    while ! curl -g -s "$JENKINS_URL/api/json" | grep -q "Authentication required"; do
        sleep 5
    done
    echo "\nJenkins está listo para iniciar sesión.\n"

    if [ -f "$FILE" ]; then
        echo "\n $FILE exists.\n"
    else 
        echo "\n $FILE does not exist.\n"
        JENKINS_USER_PASS=admin

        JENKINS_CRUMB=$(curl -u $JENKINS_USER:$JENKINS_USER_PASS -s -c /tmp/cookies "$JENKINS_URL/crumbIssuer/api/json" | grep '"crumb"' | sed 's/.*"crumb":"\([^"]*\)".*/\1/')
        ACCESS_TOKEN=$(curl -u $JENKINS_USER:$JENKINS_USER_PASS -H "Jenkins-Crumb:$JENKINS_CRUMB" -s \
                    -b /tmp/cookies "$JENKINS_URL/me/descriptorByName/jenkins.security.ApiTokenProperty/generateNewToken" \
                    --data 'newTokenName=GlobalToken' | grep '"tokenValue"' | sed 's/.*"tokenValue":"\([^"]*\)".*/\1/')       
        echo $ACCESS_TOKEN > $FILE
        echo "\nNuevo Token Api Generado\n"
    fi

    API_TOKEN=$(cat $FILE)


    response=$(curl -X POST -g -L --user $JENKINS_USER:$API_TOKEN "$JENKINS_URL/job/seed_pipeline_job/build?token=tokenSeedBuild" -v 2>&1)
    exit_code=$?

    # Verificar el código de salida
    if [ $exit_code -ne 0 ]; then
        echo "Error al ejecutar curl:"
        echo "$response"
        exit 1
    fi

    # Verificar la respuesta para mensajes de error
    if echo "$response" | grep -q "Error"; then
        echo "Error en la respuesta de curl:"
        echo "$response"
        exit 1
    else
        echo "Solicitud realizada con éxito."
    fi

    echo "\nSeed Job Launched !!!!\n"
}

launch_seed_job &

/usr/local/bin/jenkins.sh