#!/bin/sh
set -eu

# Account Service and Order Execution Persister intentionally use separate
# databases, even though this disposable container hosts both for integration.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=on <<-EOSQL
CREATE DATABASE order_execution_it OWNER ${POSTGRES_USER};
EOSQL
