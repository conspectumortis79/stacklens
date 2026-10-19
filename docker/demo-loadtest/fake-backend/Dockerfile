FROM python:3.12-slim

WORKDIR /app

COPY wheels/ wheels/
COPY requirements.txt .
RUN pip install --no-index --find-links wheels/ -r requirements.txt

COPY . .
RUN chmod +x healthcheck.sh

EXPOSE 8085

# Use gunicorn with multiple workers to simulate PHP-FPM worker pool behaviour
CMD ["gunicorn", "--workers", "4", "--threads", "2", "--bind", "0.0.0.0:8085", "--timeout", "120", "app:app"]
