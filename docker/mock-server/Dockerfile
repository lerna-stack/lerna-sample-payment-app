FROM node:12.14-alpine

CMD [ \
    "npm", "start" \
    , "--" \
    , "--host", "0.0.0.0" \
]

WORKDIR /root/

COPY [ \
    "package.json", \
    "package-lock.json", \
    "./"]

RUN npm ci

COPY [ \
    "index.js", \
    "middleware.js", \
    "./"]
