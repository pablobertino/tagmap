FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends git \
 && rm -rf /var/lib/apt/lists/*

# GoogleFindMyTools: clon fijado a un commit para builds reproducibles.
# Actualizar GFMT_REF cuando se valide una versión nueva en Fase 0.
ARG GFMT_REF=d46e952   # validado en Fase 0 (2026-09-02)
RUN git clone --depth 1 https://github.com/leonboe1/GoogleFindMyTools /app/vendor/GoogleFindMyTools \
 && cd /app/vendor/GoogleFindMyTools && git fetch --depth 1 origin ${GFMT_REF} && git checkout FETCH_HEAD || true
RUN pip install -r /app/vendor/GoogleFindMyTools/requirements.txt

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY tagmap_collector ./tagmap_collector

# Usuario sin privilegios; secrets.json se escribe en Auth/ al arrancar
RUN useradd -m app && chown -R app:app /app
USER app

ENV GFMT_REPO_PATH=/app/vendor/GoogleFindMyTools
EXPOSE 8080
CMD ["python", "-m", "tagmap_collector"]
