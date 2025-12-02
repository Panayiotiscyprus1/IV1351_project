#!/usr/bin/env bash


#BASH SCRIPT TO CREATE AND SET UP THE POSTGRESQL DATABASE FOR THE IV1351 PROJECT AND KEEP THE CONNECTION
#CHANGE SETTINGS DEPENDING ON USE CASE AND ABSOLUTE PATHS TO SQL FILES

# === Settings – change these if needed ===================
DB_USER="postgres"   # or whatever DB user you use
DB_HOST="localhost"     # usually localhost   
DB_NAME="iv1351"  # name of the database to create  
# ==========================================================


psql -h "$DB_HOST" -U "$DB_USER" -d postgres -c "DROP DATABASE $DB_NAME;" -c "CREATE DATABASE $DB_NAME;"

psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" <<EOF
\\i /Users/panayiwthzz/IV1351_project/sql/schema.sql
\\i /Users/panayiwthzz/IV1351_project/sql/functions.sql
\\i /Users/panayiwthzz/IV1351_project/sql/triggers.sql
\\i /Users/panayiwthzz/IV1351_project/sql/seeds.sql
\\i /Users/panayiwthzz/IV1351_project/sql/indexes.sql
\\i /Users/panayiwthzz/IV1351_project/sql/olap.sql
EOF


exec psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME"

