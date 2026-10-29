FROM golang:1.24.2-bookworm AS build
WORKDIR /src
RUN git clone --depth 1 --branch RELEASE.2025-08-13T08-35-41Z https://github.com/minio/mc.git .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/mc .
FROM alpine:3.22
RUN apk add --no-cache ca-certificates curl && addgroup -g 10001 app && adduser -D -u 10001 -G app app && mkdir /data && chown app:app /data
COPY --from=build /out/mc /usr/local/bin/mc
COPY --from=build /src/LICENSE /licenses/mc-LICENSE
LABEL org.opencontainers.image.source="https://github.com/minio/mc" org.opencontainers.image.version="RELEASE.2025-08-13T08-35-41Z"
USER 10001:10001
ENTRYPOINT ["mc"]
