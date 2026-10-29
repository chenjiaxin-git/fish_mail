FROM golang:1.24.2-bookworm AS build
WORKDIR /src
RUN git clone --depth 1 --branch RELEASE.2025-09-07T16-13-09Z https://github.com/minio/minio.git .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/minio .
FROM alpine:3.22
RUN apk add --no-cache ca-certificates curl && addgroup -g 10001 app && adduser -D -u 10001 -G app app && mkdir /data && chown app:app /data
COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /src/LICENSE /licenses/minio-LICENSE
LABEL org.opencontainers.image.source="https://github.com/minio/minio" org.opencontainers.image.version="RELEASE.2025-09-07T16-13-09Z"
USER 10001:10001
ENTRYPOINT ["minio"]
