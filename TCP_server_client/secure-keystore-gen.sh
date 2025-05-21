#!/bin/bash

rm -f server.keystore server.cer client.keystore client.cer client.truststore server.truststore

read -sp "Enter keystore password (min 6 chars): " KEYSTORE_PASS
echo
if [ ${#KEYSTORE_PASS} -lt 6 ]; then
    echo "Error: Keystore password must be at least 6 characters"
    exit 1
fi

read -sp "Enter key password (min 6 chars): " KEY_PASS
echo
if [ ${#KEY_PASS} -lt 6 ]; then
    echo "Error: Key password must be at least 6 characters"
    exit 1
fi

echo "export TIMESERVER_KEYSTORE_PATH=server.keystore" > timeserver.env
echo "export TIMESERVER_KEYSTORE_PASSWORD=$KEYSTORE_PASS" >> timeserver.env

keytool -genkeypair \
  -alias server \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 3650 \
  -keystore server.keystore \
  -storepass "$KEYSTORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=localhost, OU=Development, O=CPD, L=Porto, C=PT"

keytool -exportcert \
  -alias server \
  -file server.cer \
  -keystore server.keystore \
  -storepass "$KEYSTORE_PASS"

read -sp "Enter truststore password (min 6 chars): " TRUSTSTORE_PASS
echo
if [ ${#TRUSTSTORE_PASS} -lt 6 ]; then
    echo "Error: Truststore password must be at least 6 characters"
    exit 1
fi

keytool -importcert \
  -alias server \
  -file server.cer \
  -keystore client.truststore \
  -storepass "$TRUSTSTORE_PASS" \
  -noprompt

echo "export TIMECLIENT_TRUSTSTORE_PATH=client.truststore" > client.env
echo "export TIMECLIENT_TRUSTSTORE_PASSWORD=$TRUSTSTORE_PASS" >> client.env

chmod 600 timeserver.env client.env

echo
echo "===== SSL Setup Complete ====="
echo "Keystores generated successfully:"
echo " - server.keystore (server keystore)"
echo " - client.truststore (client truststore)"
echo " - server.cer (server certificate)"
echo
echo "Environment variables:"
echo " - Server: timeserver.env (source to use)"
echo " - Client: client.env (source to use)"