#!/bin/sh
python -c "import urllib.request; urllib.request.urlopen('http://localhost:8082/health')"
