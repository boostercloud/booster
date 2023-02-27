#!/usr/bin/env bash

# Run this script with the 'source' command to initialize environment variables for integration tests.

# Make sure you log in with a user that can assign the Contributor role to the service principal that will be created.
LOGIN_RESULT=$(az login)
SP_DISPLAY_NAME="$(whoami)-at-$(hostname)"

export AZURE_SUBSCRIPTION_ID=$(echo $LOGIN_RESULT | jq -r '.[].id')

SP_CREATE_RESULT=$(az ad sp create-for-rbac --name $SP_DISPLAY_NAME --role="Contributor" --scopes="/subscriptions/$AZURE_SUBSCRIPTION_ID")
export AZURE_SECRET=$(echo $SP_CREATE_RESULT | jq -r '.password')
export AZURE_APP_ID=$(echo $SP_CREATE_RESULT | jq -r '.appId')
export AZURE_TENANT_ID=$(echo $SP_CREATE_RESULT | jq -r '.tenant')

export REGION="East US" #replace with a region of your choice, see full list here: https://azure.microsoft.com/en-us/global-infrastructure/locations/

echo "Environment variables initialized:"
echo "AZURE_APP_ID = $AZURE_APP_ID"
echo "AZURE_SECRET = $AZURE_SECRET"
echo "AZURE_TENANT_ID = $AZURE_TENANT_ID"
echo "AZURE_SUBSCRIPTION_ID = $AZURE_SUBSCRIPTION_ID"
echo "REGION = $REGION"
