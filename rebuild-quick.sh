#!/bin/bash

echo "Stopping container..."
sudo docker stop vesktop-unifiedpush

echo "Rebuilding container..."
sudo docker-compose build

echo "Starting container..."
sudo docker-compose up -d

echo "Container restarted, check logs with: sudo docker logs -f vesktop-unifiedpush"
echo "Access VNC at: http://localhost:6080/vnc.html" 