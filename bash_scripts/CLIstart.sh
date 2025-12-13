#!/usr/bin/env bash

#BASH SCRIPT TO START THE CLI INTERFACE FOR THE IV1351 PROJECT

echo "Starting IV1351 CLI interface..."

mvn clean compile

mvn exec:java 
