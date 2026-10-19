FROM python:3.12-slim

WORKDIR /app
COPY wheels/ wheels/
COPY requirements.txt .
RUN pip install --no-index --find-links wheels/ -r requirements.txt
COPY metrics_server.py .
EXPOSE 9100
CMD ["python", "metrics_server.py"]
