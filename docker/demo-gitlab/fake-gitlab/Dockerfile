FROM python:3.12-slim

# Install git so the mock can serve real `git clone` over smart-HTTP.
# Without git the app can list projects via REST but every git clone
# fails with "repository not found" because the smart-HTTP endpoints
# return 404.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY wheels/ wheels/
COPY requirements.txt .
RUN pip install --no-index --find-links wheels/ -r requirements.txt

COPY . .
RUN chmod +x healthcheck.sh

# Where the bare repos for `git clone` live. Initialised from
# data.py at app startup by `setup_bare_repos()`.
RUN mkdir -p /app/repos

EXPOSE 3080

CMD ["python", "app.py"]
