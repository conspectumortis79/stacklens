FROM python:3.12-slim

WORKDIR /app

COPY wheels/ wheels/
COPY requirements.txt .
RUN pip install --no-index --find-links wheels/ -r requirements.txt

COPY . .
RUN chmod +x healthcheck.sh

EXPOSE 8082

CMD ["python", "app.py"]
